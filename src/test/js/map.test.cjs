const { test } = require("node:test");
const assert = require("node:assert/strict");
const { groupMapPoints } = require("../../main/resources/static/map.js");
const project = (item) => ({ x: item.longitude, y: item.latitude });
test("agrupa puntos cercanos sin perder sus identificadores", () => {
  const items = [
    { id: 1, latitude: 10, longitude: 10 },
    { id: 2, latitude: 12, longitude: 12 },
    { id: 3, latitude: 100, longitude: 100 },
  ];
  const groups = groupMapPoints(items, project);
  assert.deepEqual(
    groups.map((group) => group.map((item) => item.id)),
    [[1, 2], [3]],
  );
});
test("separa el grupo al aumentar la escala de proyección", () => {
  const items = [
    { latitude: 10, longitude: 10 },
    { latitude: 12, longitude: 12 },
  ];
  assert.equal(
    groupMapPoints(items, (item) => ({ x: item.longitude * 64, y: item.latitude * 64 })).length,
    2,
  );
});
test("coordenadas inválidas no producen marcadores y una consulta vacía limpia grupos", () => {
  assert.deepEqual(groupMapPoints([{ latitude: NaN, longitude: 1 }], project), []);
  assert.deepEqual(groupMapPoints([], project), []);
});
