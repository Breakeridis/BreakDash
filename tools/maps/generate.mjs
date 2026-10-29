// Asset generation only. Run checks exclusively in GitHub Actions.
import {readFileSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

export const upstream = name => JSON.parse(readFileSync(new URL(`upstream/${name}.json`, import.meta.url), 'utf8'));
export const assetPath = name => new URL(`../../app/src/main/assets/maps/driving-${name}.json`, import.meta.url);
const clone = value => structuredClone(value);
export const isRoad = layer => layer.type === 'line' && layer['source-layer'] === 'transportation'
    && /^(highway[-_]|tunnel-|bridge-)/.test(layer.id) && !/railway/.test(layer.id);
export const isRoadLabel = layer => layer['source-layer'] === 'transportation_name';

// Scale numeric outputs, keeping zoom at the top level of the expression (Native 11).
function scale(value, factor) {
    if (typeof value === 'number') return Number((value * factor).toFixed(6));
    if (!Array.isArray(value) || !['interpolate', 'step'].includes(value[0])) throw Error('Unsupported upstream size');
    const result = clone(value);
    const start = value[0] === 'interpolate' ? 4 : 2;
    for (let i = start; i < result.length; i += 2) result[i] = scale(result[i], factor);
    return result;
}

function widen(layer) {
    if (!isRoad(layer)) return [layer];
    // Split at z14: all widths below it are exactly upstream, including fractional zooms.
    // Both halves stay adjacent in the original stack, retaining bridges and junctions.
    const close = clone(layer);
    close.minzoom = Math.max(14, close.minzoom ?? 0);
    for (const key of ['line-width', 'line-gap-width', 'line-offset']) {
        if (key in close.paint) close.paint[key] = scale(close.paint[key], 1.2);
    }
    if ((layer.minzoom ?? 0) >= 14) return [close];
    if ((layer.maxzoom ?? 24) <= 14) return [layer];
    return [{...layer, id: `${layer.id}-overview`, maxzoom: 14}, close];
}

function nightRoadColor(id) {
    if (id.includes('casing')) return /motorway|trunk|primary|link/.test(id) ? '#665334' : '#303A46';
    if (id.includes('motorway')) return '#E6B55F';
    if (/trunk|primary/.test(id)) return '#D2A45F';
    if (/secondary|tertiary|link/.test(id)) return '#B9A074';
    if (id.includes('path')) return '#59626D';
    return '#939DA8';
}

// The source schema groups charging_station under fuel. Only actual fuel is wanted.
// Car parking uses parking/parking_garage, excluding bicycle/motorcycle parking.
export const poiFilter = ['all',
    ['match', ['geometry-type'], ['Point', 'MultiPoint'], true, false],
    ['any', ['==', ['get', 'subclass'], 'fuel'],
        ['all', ['==', ['get', 'class'], 'fuel'], ['!', ['has', 'subclass']]],
        ['match', ['get', 'class'], ['parking', 'parking_garage'], true, false],
        ['match', ['get', 'subclass'], ['parking', 'parking_garage'], true, false]]];

function poi(night) {
    return {
        id: 'driving-fuel-parking', type: 'symbol', source: 'openmaptiles', 'source-layer': 'poi', minzoom: 15,
        filter: clone(poiFilter),
        layout: {
            'icon-image': ['case', ['any', ['==', ['get', 'subclass'], 'fuel'], ['==', ['get', 'class'], 'fuel']], 'fuel', 'parking'],
            'icon-size': 1.2, 'icon-padding': 4,
            'icon-allow-overlap': false, 'icon-ignore-placement': false,
            'text-allow-overlap': false, 'text-ignore-placement': false, 'text-optional': true,
            'text-field': ['step', ['zoom'], '', 16, ['coalesce', ['get', 'name'], ['get', 'name:latin'], ['get', 'name_en'], '']],
            'text-font': ['Noto Sans Regular'], 'text-size': 14, 'text-anchor': 'top', 'text-offset': [0, 1],
            'text-max-width': 9, 'text-padding': 3
        },
        paint: {'text-color': night ? '#E1E6EC' : '#25303B', 'text-halo-color': night ? '#080A0D' : '#FFFFFF', 'text-halo-width': 1.5}
    };
}

export function generate(night) {
    const bright = upstream('bright');
    const style = night ? upstream('dark') : clone(bright);
    style.name = `BreakDash driving ${night ? 'night' : 'day'}`;
    style.metadata = {
        'breakdash:upstream': night ? 'OpenFreeMap Dark / Dark Matter, with Bright road hierarchy' : 'OpenFreeMap Bright / OSM Bright',
        'breakdash:notice': 'See maps/NOTICE.txt and maps/licenses/ in this APK',
        'breakdash:revision': 1
    };
    if (night) {
        // Dark's original road stack flattens brunnels and road hierarchy. Reuse Bright's
        // complete transportation stack and shields, recoloured below, over Dark's base.
        style.layers = style.layers.filter(l => !['transportation', 'transportation_name'].includes(l['source-layer']));
        const roads = bright.layers.filter(l => l['source-layer'] === 'transportation');
        const labels = bright.layers.filter(isRoadLabel);
        const boundary = style.layers.findIndex(l => l['source-layer'] === 'boundary');
        style.layers.splice(boundary, 0, ...clone(roads), ...clone(labels));
    }
    // Drop the shifted building-top imitation of height and all business/transit symbols.
    style.layers = style.layers.filter(l => l.id !== 'building-top'
        && !['poi', 'aerodrome_label'].includes(l['source-layer']));
    for (const layer of style.layers) {
        const paint = layer.paint ??= {};
        if (layer['source-layer'] === 'building') {
            paint['fill-color'] = night ? '#161C23' : '#DDDAD5';
            paint['fill-outline-color'] = night ? '#202833' : '#C9C7C3';
            paint['fill-opacity'] = 0.65;
        }
        if (isRoad(layer)) {
            if (night) paint['line-color'] = nightRoadColor(layer.id);
            else if (layer.id.includes('casing')) {
                paint['line-color'] = /motorway|trunk|primary/.test(layer.id) ? '#AC793F' : '#87919B';
            }
        }
        if (night) {
            if (layer.type === 'background') paint['background-color'] = '#080A0D';
            if (layer['source-layer'] === 'transportation' && layer.type === 'fill') {
                paint['fill-color'] = '#303A46'; paint['fill-outline-color'] = '#414C59';
            }
            if (layer['source-layer'] === 'transportation' && layer.type === 'line' && !isRoad(layer)) paint['line-color'] = '#414C59';
            if (['landuse', 'landcover'].includes(layer['source-layer'])) paint['fill-color'] = layer.id.includes('wood') || layer.id.includes('park') ? '#122018' : '#10151B';
            if (layer['source-layer'] === 'water') paint['fill-color'] = '#0B1926';
            if (layer['source-layer'] === 'place') {
                paint['text-color'] = '#C2CBD5'; paint['text-halo-color'] = '#080A0D';
                // Dark's sprite refers to an absent circle-11; use the hosted OFM name.
                if (layer.layout?.['icon-image']) layer.layout['icon-image'] = ['step', ['zoom'], 'circle_11', 9, ''];
            }
        }
        if (isRoadLabel(layer) && !layer.id.includes('shield')) {
            layer.layout['text-size'] = scale(layer.layout['text-size'], 1.2);
            paint['text-color'] = night ? '#F0E9DB' : '#18212B';
            paint['text-halo-color'] = night ? '#080A0D' : '#FFFFFF';
            paint['text-halo-width'] = 1.5; paint['text-halo-blur'] = 0.25;
        }
        if (layer.type === 'symbol') {
            layer.layout ??= {};
            for (const key of ['icon-allow-overlap', 'text-allow-overlap', 'icon-ignore-placement', 'text-ignore-placement']) layer.layout[key] = false;
        }
    }
    style.layers = style.layers.flatMap(widen);
    // Symbols are placed from the top of the stack downward. Road names and shields
    // follow POIs in the style, so they keep priority during collision placement.
    style.layers.splice(style.layers.findIndex(isRoadLabel), 0, poi(night));
    return style;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
    for (const [name, night] of [['day', false], ['night', true]]) {
        writeFileSync(assetPath(name), JSON.stringify(generate(night), null, 2) + '\n');
    }
}
