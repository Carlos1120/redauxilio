"use strict";
/**
 * Borradores locales. Los de cuenta se cifran con AES-GCM y una clave obtenida solo de una
 * sesión autenticada; la clave nunca se persiste en el navegador. Los de visitantes son
 * independientes y no se atribuyen automáticamente a ninguna cuenta.
 */
(() => {
  const DB_NAME = "redauxilio-secure-drafts";
  const STORE = "drafts";
  let accountScope = null;
  let visitorScope = null;
  let epoch = 0;
  const authenticated = () =>
    document.getElementById("identity-open")?.dataset.authenticated === "true";

  const database = new Promise((resolve, reject) => {
    if (!window.indexedDB) {
      reject(new Error("Almacenamiento local no disponible."));
      return;
    }
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });

  async function transaction(mode, run) {
    const db = await database;
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, mode);
      const request = run(tx.objectStore(STORE));
      let value;
      request.onsuccess = () => {
        value = request.result;
      };
      tx.oncomplete = () => resolve(value);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  }

  function forgetSession() {
    epoch += 1;
    accountScope = null;
  }

  async function scope() {
    if (!authenticated()) {
      // Una visita anónima no tiene identidad comprobable: su clave solo dura la sesión
      // de esta pestaña. El borrador puede recuperarse offline durante esa sesión.
      if (visitorScope) return visitorScope;
      if (!crypto?.subtle) throw new Error("Se necesita una conexión segura para guardar borradores.");
      let encoded = sessionStorage.getItem("redauxilio:visitor-draft-key");
      if (!encoded) {
        const generated = crypto.getRandomValues(new Uint8Array(32));
        encoded = btoa(String.fromCharCode(...generated));
        sessionStorage.setItem("redauxilio:visitor-draft-key", encoded);
        generated.fill(0);
      }
      const raw = Uint8Array.from(atob(encoded), (character) => character.charCodeAt(0));
      const key = await crypto.subtle.importKey("raw", raw, { name: "AES-GCM" }, false, [
        "encrypt",
        "decrypt",
      ]);
      raw.fill(0);
      visitorScope = { slot: "visitor", key };
      return visitorScope;
    }
    if (accountScope) return accountScope;
    if (!crypto?.subtle) throw new Error("El navegador no permite cifrar borradores privados.");
    const expectedEpoch = epoch;
    let response;
    try {
      response = await fetch("/api/my/draft-key", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { Accept: "application/json" },
      });
    } catch {
      throw new Error(
        "No se pudo verificar tu cuenta. Conéctate para guardar el borrador privado.",
      );
    }
    if (!response.ok) throw new Error("No se pudo verificar tu cuenta para proteger el borrador.");
    const data = await response.json();
    if (!/^\d+$/u.test(String(data.owner)) || !/^[A-Za-z0-9_-]{43}$/u.test(data.key || ""))
      throw new Error("Respuesta de seguridad no válida.");
    const base64 = data.key.replace(/-/gu, "+").replace(/_/gu, "/") + "=";
    const bytes = Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
    const key = await crypto.subtle.importKey("raw", bytes, { name: "AES-GCM" }, false, [
      "encrypt",
      "decrypt",
    ]);
    bytes.fill(0);
    if (epoch !== expectedEpoch || !authenticated()) throw new Error("La sesión cambió.");
    accountScope = { slot: "account:" + data.owner, key };
    return accountScope;
  }

  async function encrypt(key, bytes) {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const ciphertext = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, bytes);
    return { iv: Array.from(iv), ciphertext };
  }

  async function decrypt(key, record) {
    return crypto.subtle.decrypt(
      { name: "AES-GCM", iv: new Uint8Array(record.iv) },
      key,
      record.ciphertext,
    );
  }

  async function pack(draft, key) {
    const evidence = Array.isArray(draft.evidence) ? draft.evidence : [];
    const files = [];
    for (const file of evidence) files.push(await encrypt(key, await file.arrayBuffer()));
    const plain = {
      ...draft,
      evidence: [],
      evidenceMetadata: evidence.map((file) => ({ name: file.name, type: file.type })),
    };
    const payload = new TextEncoder().encode(JSON.stringify(plain));
    return { version: 1, protected: true, body: await encrypt(key, payload), files };
  }

  async function unpack(record, key) {
    const bytes = await decrypt(key, record.body);
    const draft = JSON.parse(new TextDecoder().decode(bytes));
    const metadata = Array.isArray(draft.evidenceMetadata) ? draft.evidenceMetadata : [];
    delete draft.evidenceMetadata;
    draft.evidence = [];
    for (let i = 0; i < (record.files || []).length; i += 1) {
      const decrypted = await decrypt(key, record.files[i]);
      draft.evidence.push(
        new File([decrypted], metadata[i]?.name || "evidencia", {
          type: metadata[i]?.type || "application/octet-stream",
        }),
      );
    }
    return draft;
  }

  async function save(draft) {
    const started = epoch;
    const owner = await scope();
    const record = await pack(draft, owner.key);
    if (epoch !== started || (owner.slot.startsWith("account:") && !authenticated()))
      throw new Error("La sesión cambió. No se guardó el borrador.");
    await transaction("readwrite", (store) => store.put(record, owner.slot));
  }

  async function read() {
    const started = epoch;
    const owner = await scope();
    const record = await transaction("readonly", (store) => store.get(owner.slot));
    if (epoch !== started || (owner.slot.startsWith("account:") && !authenticated()))
      throw new Error("La sesión cambió. No se mostrará el borrador anterior.");
    if (!record) return null;
    // Se rechazan incluso los registros anónimos antiguos almacenados sin cifrar.
    if (!record.protected) throw new Error("El borrador no está cifrado y no puede abrirse.");
    try {
      return await unpack(record, owner.key);
    } catch {
      throw new Error("No se pudo recuperar el borrador: la sesión o la clave cambió.");
    }
  }

  async function clear() {
    const started = epoch;
    const owner = await scope();
    if (epoch !== started || (owner.slot.startsWith("account:") && !authenticated())) throw new Error("La sesión cambió.");
    await transaction("readwrite", (store) => store.delete(owner.slot));
  }

  const channel =
    typeof BroadcastChannel === "function"
      ? new BroadcastChannel("redauxilio-draft-session")
      : null;
  channel?.addEventListener("message", () => {
    forgetSession();
    window.dispatchEvent(new Event("redauxilio:draft-session-invalidated"));
  });
  window.addEventListener("redauxilio:identity-change", () => {
    forgetSession();
    channel?.postMessage("session-updated");
  });
  window.RedAuxilioDraftVault = { read, save, clear, forgetSession };
})();
