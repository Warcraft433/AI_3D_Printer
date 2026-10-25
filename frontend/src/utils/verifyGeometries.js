import { parseGCode } from './gcodeParser.js';
import http from 'http';
import fs from 'fs';
import path from 'path';

// Let's test on the genuine sliced files corresponding to these jobs
const gcodeDir = path.resolve('..', 'backend', 'media', 'gcode');

const models = [
    { name: 'Calibration Cube (Job 35 / test_cube)', file: 'test_cube.gcode', expectedLayers: 70, expectedTime: '14m 38s', minZ: 0.35, maxZ: 21.05 },
    { name: 'Ceramic Vase (Job 36 / test_cup)', file: 'test_cup.gcode', expectedLayers: 140, expectedTime: '52m 55s', minZ: 0.35, maxZ: 42.05 },
    { name: 'Spur Gear (test_gear)', file: 'test_gear.gcode', expectedLayers: 40, expectedTime: '19m 55s', minZ: 0.35, maxZ: 12.05 },
];

console.log('=== Automated Geometry & Layer Verification ===');

for (const m of models) {
    const fullPath = path.join(gcodeDir, m.file);
    const content = fs.readFileSync(fullPath, 'utf-8');
    const parsed = parseGCode(content);

    console.log(`\nTesting: ${m.name}`);
    console.log(`- Layers: ${parsed.totalLayers} (expected ${m.expectedLayers})`);
    console.log(`- Est Time: ${parsed.stats.estimatedPrintTime} (expected ${m.expectedTime})`);
    console.log(`- First Layer Z: ${parsed.layers[0].z} mm (expected ${m.minZ})`);
    console.log(`- Top Layer Z: ${parsed.layers[parsed.totalLayers - 1].z} mm (expected ${m.maxZ})`);

    if (parsed.totalLayers !== m.expectedLayers) {
        throw new Error(`Layer count mismatch for ${m.name}: got ${parsed.totalLayers}, expected ${m.expectedLayers}`);
    }
    if (parsed.stats.estimatedPrintTime !== m.expectedTime) {
        throw new Error(`Estimated time mismatch for ${m.name}: got ${parsed.stats.estimatedPrintTime}, expected ${m.expectedTime}`);
    }
    if (Math.abs(parsed.layers[0].z - m.minZ) > 0.01) {
        throw new Error(`First layer Z mismatch for ${m.name}: got ${parsed.layers[0].z}, expected ${m.minZ}`);
    }
    if (Math.abs(parsed.layers[parsed.totalLayers - 1].z - m.maxZ) > 0.01) {
        throw new Error(`Top layer Z mismatch for ${m.name}: got ${parsed.layers[parsed.totalLayers - 1].z}, expected ${m.maxZ}`);
    }

    console.log(`✓ Verification PASSED for ${m.name}`);
}

console.log('\nALL 3 GEOMETRY TESTS PASSED WITH 100% ACCURACY!');
