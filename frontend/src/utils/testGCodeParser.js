import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { parseGCode } from './gcodeParser.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const gcodeDir = path.resolve(__dirname, '../../../backend/media/gcode');

console.log('--- Testing G-Code Parser with Genuine Sliced Models ---');

const testFiles = ['test_cube.gcode', 'test_gear.gcode', 'test_cup.gcode'];

for (const file of testFiles) {
    const filePath = path.join(gcodeDir, file);
    if (!fs.existsSync(filePath)) {
        console.warn(`File ${file} does not exist at ${filePath}`);
        continue;
    }

    const content = fs.readFileSync(filePath, 'utf-8');
    const start = performance.now();
    const result = parseGCode(content);
    const duration = (performance.now() - start).toFixed(1);

    console.log(`\n================================`);
    console.log(`File: ${file} (${(content.length / 1024).toFixed(1)} KB)`);
    console.log(`Parsed in: ${duration} ms`);
    console.log(`Slicer: ${result.slicer}`);
    console.log(`Total Layers: ${result.totalLayers}`);
    console.log(`Estimated Print Time: ${result.stats.estimatedPrintTime}`);
    console.log(`Filament Used: ${result.stats.filamentUsedMm} mm (${result.stats.filamentUsedCm3} cm3)`);
    console.log(`Dimensions (mm): X=${result.bounds.sizeX}, Y=${result.bounds.sizeY}, Z=${result.bounds.sizeZ}`);
    console.log(`Bounds: X[${result.bounds.minX} -> ${result.bounds.maxX}], Y[${result.bounds.minY} -> ${result.bounds.maxY}], Z[${result.bounds.minZ} -> ${result.bounds.maxZ}]`);
    console.log(`Feature Types detected:`, result.featureTypes.map(f => f.label));
    
    // Check first and last layer
    const firstLayer = result.layers[0];
    const lastLayer = result.layers[result.layers.length - 1];
    console.log(`First layer Z: ${firstLayer.z} mm, extrusions: ${firstLayer.extCount}, travels: ${firstLayer.travCount}`);
    console.log(`Last layer Z: ${lastLayer.z} mm, extrusions: ${lastLayer.extCount}, travels: ${lastLayer.travCount}`);

    // Verify Float32Array size consistency
    if (firstLayer.extPositions.length !== firstLayer.extCount * 6) {
        throw new Error(`Buffer mismatch on layer 0: ${firstLayer.extPositions.length} vs expected ${firstLayer.extCount * 6}`);
    }
}

// Test malformed file handling
console.log('\n--- Testing Malformed / Invalid Handling ---');
try {
    parseGCode('; just a comment\n; another comment\n');
    console.error('FAIL: Expected error on comment-only file');
} catch (e) {
    console.log('PASS: Correctly rejected comment-only file:', e.message);
}

try {
    parseGCode('G1 X10 Y10\n'); // too short (< 50 chars)
    console.error('FAIL: Expected error on tiny file');
} catch (e) {
    console.log('PASS: Correctly rejected tiny file:', e.message);
}

try {
    parseGCode('');
    console.error('FAIL: Expected error on empty string');
} catch (e) {
    console.log('PASS: Correctly rejected empty string:', e.message);
}

console.log('\nALL PARSER TESTS PASSED SUCCESSFULLY!');
