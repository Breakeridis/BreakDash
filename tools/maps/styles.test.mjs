// GitHub Actions only: spec validation plus driving-style acceptance contracts.
import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {validateStyleMin, createExpression} from '@maplibre/maplibre-gl-style-spec';
import {assetPath, generate, upstream} from './generate.mjs';

const bright = upstream('bright');
const sprites = upstream('sprite');
function evaluate(value, zoom, properties = {}) {
    const compiled = createExpression(value);
    assert.equal(compiled.result, 'success', JSON.stringify(compiled.value));
    return compiled.value.evaluate({zoom}, {type: 'Point', properties});
}
const close = (a, b) => assert.ok(Math.abs(a - b) < 0.00001, `${a} != ${b}`);

for (const [name, night] of [['day', false], ['night', true]]) {
    const style = JSON.parse(readFileSync(assetPath(name), 'utf8'));
    const byId = id => {
        const layer = style.layers.find(l => l.id === id);
        assert.ok(layer, `Missing ${id}`); return layer;
    };
    test(`${name}: valid MapLibre v8 style and reproducible derivative`, () => {
        assert.deepEqual(validateStyleMin(style).map(e => e.message), []);
        assert.deepEqual(style, generate(night));
        assert.equal(new Set(style.layers.map(l => l.id)).size, style.layers.length);
        assert.ok(!style.layers.some(l => l.type === 'fill-extrusion'));
        assert.ok(!style.layers.some(l => l.id === 'building-top'));
    });
    test(`${name}: remote resources remain on OpenFreeMap`, () => {
        assert.deepEqual(style.sources, bright.sources);
        assert.equal(style.sources.openmaptiles.url, 'https://tiles.openfreemap.org/planet');
        assert.equal(style.glyphs, 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf');
        assert.equal(style.sprite, bright.sprite);
        for (const source of Object.values(style.sources)) {
            for (const url of [source.url, ...(source.tiles ?? [])].filter(Boolean)) assert.equal(new URL(url).host, 'tiles.openfreemap.org');
        }
        for (const icon of ['fuel', 'parking', 'road_1', 'road_6']) assert.ok(sprites[icon], icon);
    });
    test(`${name}: roads preserve original geometry and widen only at z14`, () => {
        const roads = bright.layers.filter(l => l.type === 'line' && /^(highway-|bridge-|tunnel-)/.test(l.id) && !l.id.includes('railway'));
        for (const original of roads) {
            for (const z of [12, 13.9, 14, 15.5, 16.5, 20]) {
                if (z < (original.minzoom ?? 0) || z >= (original.maxzoom ?? 24)) continue;
                const candidates = style.layers.filter(l => (l.id === original.id || l.id === `${original.id}-overview`)
                    && z >= (l.minzoom ?? 0) && z < (l.maxzoom ?? 24));
                assert.equal(candidates.length, 1, `${original.id} z${z}`);
                const actual = candidates[0];
                assert.deepEqual(actual.filter, original.filter);
                assert.deepEqual(actual.layout, original.layout);
                assert.deepEqual(actual.paint['line-dasharray'], original.paint['line-dasharray']);
                close(evaluate(actual.paint['line-width'], z), evaluate(original.paint['line-width'], z) * (z >= 14 ? 1.2 : 1));
                if (!night && !original.id.includes('casing')) assert.deepEqual(actual.paint['line-color'], original.paint['line-color']);
            }
        }
        assert.ok(byId('bridge-motorway').filter);
        assert.ok(byId('tunnel-motorway').paint['line-dasharray']);
    });
    test(`${name}: road names grow 20%, shields and towns survive, collisions stay enabled`, () => {
        for (const original of bright.layers.filter(l => l['source-layer'] === 'transportation_name')) {
            const actual = byId(original.id);
            assert.deepEqual(actual.filter, original.filter);
            assert.deepEqual(actual.layout['text-field'], original.layout['text-field']);
            for (const zoom of [14, 15.5, 16.5, 20]) close(evaluate(actual.layout['text-size'], zoom),
                evaluate(original.layout['text-size'], zoom) * (original.id.includes('shield') ? 1 : 1.2));
        }
        assert.ok(style.layers.some(l => l['source-layer'] === 'place'));
        assert.ok(!style.layers.some(l => l['source-layer'] === 'aerodrome_label'));
        for (const l of style.layers.filter(l => l.type === 'symbol')) {
            for (const key of ['icon-allow-overlap', 'text-allow-overlap', 'icon-ignore-placement', 'text-ignore-placement']) assert.equal(l.layout[key], false, `${l.id} ${key}`);
        }
    });
    test(`${name}: fuel and car parking, no rank gates or other POIs, icons z15 / names z16`, () => {
        const pois = style.layers.filter(l => l['source-layer'] === 'poi');
        assert.equal(pois.length, 1);
        const poi = pois[0]; assert.equal(poi.minzoom, 15);
        assert.ok(style.layers.indexOf(poi) < style.layers.indexOf(byId('highway-name-minor')));
        for (const properties of [{class: 'fuel', subclass: 'fuel', rank: 99}, {class: 'fuel'},
            {class: 'parking', subclass: 'parking'}, {class: 'parking_garage'}, {class: 'other', subclass: 'parking'}]) {
            assert.equal(evaluate(poi.filter, 15, properties), true, JSON.stringify(properties));
        }
        for (const properties of [{class: 'fuel', subclass: 'charging_station'}, {class: 'bicycle_parking'},
            {class: 'motorcycle_parking'}, {class: 'cafe'}, {class: 'bus'}, {class: 'rail'}, {}]) {
            assert.equal(evaluate(poi.filter, 15, properties), false, JSON.stringify(properties));
        }
        assert.equal(evaluate(poi.layout['text-field'], 15.99, {name: 'Station'}).toString(), '');
        assert.equal(evaluate(poi.layout['text-field'], 16, {name: 'Station'}).toString(), 'Station');
        assert.equal(poi.layout['text-optional'], true);
        assert.equal(evaluate(poi.layout['icon-image'], 15, {class: 'fuel', subclass: 'fuel'}).toString(), 'fuel');
        assert.equal(evaluate(poi.layout['icon-image'], 15, {class: 'parking'}).toString(), 'parking');
    });
    if (night) test('night: near-black base, amber major roads and subdued flat buildings', () => {
        assert.equal(byId('background').paint['background-color'], '#080A0D');
        assert.equal(byId('highway-motorway').paint['line-color'], '#E6B55F');
        assert.equal(byId('highway-minor').paint['line-color'], '#939DA8');
        assert.equal(byId('building').type, 'fill');
        assert.equal(byId('building').paint['fill-color'], '#161C23');
    });
}
