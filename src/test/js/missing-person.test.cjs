const test = require("node:test"),
  assert = require("node:assert/strict"),
  fs = require("node:fs"),
  path = require("node:path"),
  vm = require("node:vm");
/** No-op de fecha: conserva precisión de origen incluso si el navegador normaliza datetime-local. */
test("fecha de desaparecido conserva instante original al no editar y convierte cambio local", () => {
  const context = { window: {} };
  vm.runInNewContext(
    fs.readFileSync(path.join(__dirname, "../../main/resources/static/missing-person.js"), "utf8"),
    context,
  );
  const input = { value: "", dataset: {} };
  const api = context.window.RedAuxilioMissingPerson;
  api.setSeenTime(input, "2026-11-03T08:30:45.123456Z");
  assert.equal(api.readSeenTime(input), "2026-11-03T08:30:45.123456Z");
  input.value = "2026-11-03T08:31";
  assert.match(api.readSeenTime(input), /Z$/);
  assert.notEqual(api.readSeenTime(input), "2026-11-03T08:30:45.123456Z");
  api.setSeenTime(input, null);
  assert.equal(api.readSeenTime(input), null);
});
