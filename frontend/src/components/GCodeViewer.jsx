import React, { useEffect, useRef, useState, useCallback } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { 
    Play, Pause, RotateCcw, ChevronLeft, ChevronRight, 
    ZoomIn, ZoomOut, Eye, Layers, Maximize2, Compass
} from 'lucide-react';
import { TOOLPATH_COLORS } from '../utils/gcodeParser';

export default function GCodeViewer({ parsedData, jobTitle }) {
    const containerRef = useRef(null);
    const canvasRef = useRef(null);

    // Three.js instances ref
    const threeRef = useRef({
        scene: null,
        camera: null,
        renderer: null,
        controls: null,
        layerExtMeshes: [],
        layerTravMeshes: [],
        bedGroup: null,
        animFrameId: null,
        initialCameraPos: null,
        initialTarget: null
    });

    // Viewer states
    const [currentLayer, setCurrentLayer] = useState(1); // 1-indexed for display
    const [isPlaying, setIsPlaying] = useState(false);
    const [playbackSpeed, setPlaybackSpeed] = useState(1); // 0.5x, 1x, 2x, 5x, 10x
    const [showTravelMoves, setShowTravelMoves] = useState(false);
    const [viewMode, setViewMode] = useState('accumulative'); // 'accumulative' | 'single'
    const [cameraView, setCameraView] = useState('iso'); // 'iso' | 'top' | 'front' | 'side'

    const totalLayers = parsedData?.totalLayers || 1;
    const activeLayerData = parsedData?.layers ? parsedData.layers[currentLayer - 1] : null;

    // Initialize Three.js Scene
    useEffect(() => {
        if (!containerRef.current || !canvasRef.current || !parsedData) return;

        const container = containerRef.current;
        const width = container.clientWidth || 800;
        const height = container.clientHeight || 500;

        // Scene
        const scene = new THREE.Scene();
        scene.background = new THREE.Color(0x0a0d14);

        // Camera
        const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 2000);
        
        // Renderer
        const renderer = new THREE.WebGLRenderer({
            canvas: canvasRef.current,
            antialias: true,
            powerPreference: 'high-performance'
        });
        renderer.setSize(width, height);
        renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));

        // Controls
        const controls = new OrbitControls(camera, renderer.domElement);
        controls.enableDamping = true;
        controls.dampingFactor = 0.08;
        controls.maxDistance = 800;
        controls.minDistance = 5;

        // Lighting
        const ambientLight = new THREE.AmbientLight(0xffffff, 0.85);
        scene.add(ambientLight);

        const dirLight = new THREE.DirectionalLight(0xffffff, 0.6);
        dirLight.position.set(50, 150, 100);
        scene.add(dirLight);

        // Build Print Bed on X-Z plane (Y = 0)
        const bedGroup = new THREE.Group();
        const bedSize = 220; // 220x220 mm standard bed
        
        // Subtle grid
        const gridHelper = new THREE.GridHelper(bedSize, 22, 0x38bdf8, 0x1e293b);
        gridHelper.position.y = -0.05;
        bedGroup.add(gridHelper);

        // Bed plate surface (semi-transparent dark glass)
        const bedGeo = new THREE.PlaneGeometry(bedSize, bedSize);
        const bedMat = new THREE.MeshBasicMaterial({
            color: 0x0f172a,
            transparent: true,
            opacity: 0.6,
            depthWrite: false,
            side: THREE.DoubleSide
        });
        const bedMesh = new THREE.Mesh(bedGeo, bedMat);
        bedMesh.rotation.x = Math.PI / 2;
        bedGroup.add(bedMesh);

        // Bed border wireframe
        const borderGeo = new THREE.EdgesGeometry(new THREE.PlaneGeometry(bedSize, bedSize));
        const borderMat = new THREE.LineBasicMaterial({ color: 0x0284c7, transparent: true, opacity: 0.4 });
        const borderLines = new THREE.LineSegments(borderGeo, borderMat);
        borderLines.rotation.x = Math.PI / 2;
        bedGroup.add(borderLines);

        scene.add(bedGroup);

        // Position camera to fit model nicely
        const bounds = parsedData.bounds;
        const maxDim = Math.max(bounds.sizeX, bounds.sizeY, bounds.sizeZ, 30);
        const camDist = maxDim * 2.2;
        
        camera.position.set(camDist * 0.7, camDist * 0.8, camDist * 0.9);
        controls.target.set(0, (bounds.sizeZ || 20) / 2, 0);
        controls.update();

        threeRef.current.initialCameraPos = camera.position.clone();
        threeRef.current.initialTarget = controls.target.clone();

        // Build layer line meshes
        const layerExtMeshes = [];
        const layerTravMeshes = [];

        const extGroup = new THREE.Group();
        const travGroup = new THREE.Group();

        parsedData.layers.forEach((layer) => {
            // Extrusions
            if (layer.extCount > 0) {
                const extGeo = new THREE.BufferGeometry();
                extGeo.setAttribute('position', new THREE.BufferAttribute(layer.extPositions, 3));
                extGeo.setAttribute('color', new THREE.BufferAttribute(layer.extColors, 3));

                const extMat = new THREE.LineBasicMaterial({
                    vertexColors: true,
                    transparent: true,
                    opacity: 0.95,
                    blending: THREE.NormalBlending
                });

                const extLine = new THREE.LineSegments(extGeo, extMat);
                extGroup.add(extLine);
                layerExtMeshes.push(extLine);
            } else {
                layerExtMeshes.push(null);
            }

            // Travels
            if (layer.travCount > 0) {
                const travGeo = new THREE.BufferGeometry();
                travGeo.setAttribute('position', new THREE.BufferAttribute(layer.travPositions, 3));

                const travMat = new THREE.LineBasicMaterial({
                    color: 0x64748b,
                    transparent: true,
                    opacity: 0.35
                });

                const travLine = new THREE.LineSegments(travGeo, travMat);
                travLine.visible = false;
                travGroup.add(travLine);
                layerTravMeshes.push(travLine);
            } else {
                layerTravMeshes.push(null);
            }
        });

        scene.add(extGroup);
        scene.add(travGroup);

        threeRef.current.scene = scene;
        threeRef.current.camera = camera;
        threeRef.current.renderer = renderer;
        threeRef.current.controls = controls;
        threeRef.current.layerExtMeshes = layerExtMeshes;
        threeRef.current.layerTravMeshes = layerTravMeshes;
        threeRef.current.bedGroup = bedGroup;

        // Render loop
        let isRunning = true;
        const animate = () => {
            if (!isRunning) return;
            threeRef.current.animFrameId = requestAnimationFrame(animate);
            controls.update();
            renderer.render(scene, camera);
        };
        animate();

        // Resize observer
        const handleResize = () => {
            if (!containerRef.current || !renderer || !camera) return;
            const w = containerRef.current.clientWidth;
            const h = containerRef.current.clientHeight;
            camera.aspect = w / h;
            camera.updateProjectionMatrix();
            renderer.setSize(w, h);
        };
        const resizeObs = new ResizeObserver(handleResize);
        resizeObs.observe(containerRef.current);

        return () => {
            isRunning = false;
            if (threeRef.current.animFrameId) {
                cancelAnimationFrame(threeRef.current.animFrameId);
            }
            resizeObs.disconnect();
            renderer.dispose();
            scene.clear();
        };
    }, [parsedData]);

    // Update layer visibility when currentLayer, viewMode, or showTravelMoves changes
    useEffect(() => {
        const { layerExtMeshes, layerTravMeshes } = threeRef.current;
        if (!layerExtMeshes || layerExtMeshes.length === 0) return;

        const currentIdx = currentLayer - 1;

        layerExtMeshes.forEach((mesh, idx) => {
            if (!mesh) return;
            if (viewMode === 'accumulative') {
                mesh.visible = idx <= currentIdx;
                // Highlight active top layer
                if (idx === currentIdx) {
                    mesh.material.opacity = 1.0;
                } else {
                    mesh.material.opacity = 0.85;
                }
            } else {
                // Single layer mode
                mesh.visible = idx === currentIdx;
                mesh.material.opacity = 1.0;
            }
        });

        layerTravMeshes.forEach((mesh, idx) => {
            if (!mesh) return;
            if (!showTravelMoves) {
                mesh.visible = false;
            } else if (viewMode === 'accumulative') {
                mesh.visible = idx === currentIdx; // only show travel moves for active layer to avoid clutter
            } else {
                mesh.visible = idx === currentIdx;
            }
        });
    }, [currentLayer, viewMode, showTravelMoves]);

    // Playback Animation Loop
    useEffect(() => {
        if (!isPlaying) return;

        const intervalMs = Math.max(30, Math.round(180 / playbackSpeed));
        const timer = setInterval(() => {
            setCurrentLayer((prev) => {
                if (prev >= totalLayers) {
                    setIsPlaying(false);
                    return prev;
                }
                return prev + 1;
            });
        }, intervalMs);

        return () => clearInterval(timer);
    }, [isPlaying, playbackSpeed, totalLayers]);

    // Keyboard navigation (Arrow keys and Spacebar)
    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.target.tagName === 'INPUT') return;
            if (e.code === 'ArrowRight' || e.code === 'KeyD') {
                e.preventDefault();
                setCurrentLayer((l) => Math.min(totalLayers, l + 1));
            } else if (e.code === 'ArrowLeft' || e.code === 'KeyA') {
                e.preventDefault();
                setCurrentLayer((l) => Math.max(1, l - 1));
            } else if (e.code === 'Space') {
                e.preventDefault();
                setIsPlaying((p) => !p);
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [totalLayers]);

    // Camera view presets
    const setCameraPreset = (preset) => {
        const { camera, controls } = threeRef.current;
        if (!camera || !controls || !parsedData) return;

        const bounds = parsedData.bounds;
        const maxDim = Math.max(bounds.sizeX, bounds.sizeY, bounds.sizeZ, 30);
        const dist = maxDim * 2.2;
        const targetY = (bounds.sizeZ || 20) / 2;

        controls.target.set(0, targetY, 0);

        if (preset === 'top') {
            camera.position.set(0, dist * 1.5, 0.001); // Looking straight down
        } else if (preset === 'front') {
            camera.position.set(0, targetY, dist); // Front elevation
        } else if (preset === 'side') {
            camera.position.set(dist, targetY, 0); // Side profile
        } else {
            // Isometric
            camera.position.set(dist * 0.7, dist * 0.8, dist * 0.9);
        }

        camera.lookAt(controls.target);
        controls.update();
        setCameraView(preset);
    };

    const resetCamera = () => {
        const { camera, controls, initialCameraPos, initialTarget } = threeRef.current;
        if (camera && controls && initialCameraPos && initialTarget) {
            camera.position.copy(initialCameraPos);
            controls.target.copy(initialTarget);
            controls.update();
            setCameraView('iso');
        }
    };

    const handleZoom = (factor) => {
        const { camera, controls } = threeRef.current;
        if (!camera || !controls) return;
        camera.position.sub(controls.target).multiplyScalar(factor).add(controls.target);
        controls.update();
    };

    return (
        <div className="flex flex-col h-full w-full bg-slate-950 text-slate-100 rounded-xl overflow-hidden border border-slate-800 shadow-2xl relative select-none">
            {/* Top Toolbar / Header */}
            <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 bg-slate-900/90 border-b border-slate-800 backdrop-blur z-10">
                <div className="flex items-center gap-3">
                    <div className="p-2 rounded-lg bg-cyan-500/10 border border-cyan-500/20 text-cyan-400">
                        <Layers size={18} />
                    </div>
                    <div>
                        <h3 className="font-semibold text-sm text-white flex items-center gap-2">
                            <span>{jobTitle || 'G-Code Toolpath Preview'}</span>
                            <span className="text-xs font-normal px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700">
                                {parsedData.slicer.split(' on ')[0] || 'PrusaSlicer'}
                            </span>
                        </h3>
                        <p className="text-xs text-slate-400 flex items-center gap-2 mt-0.5">
                            <span>Bed: {parsedData.stats.bedShape}</span>
                            <span>•</span>
                            <span>Est. Time: <strong className="text-cyan-300 font-mono">{parsedData.stats.estimatedPrintTime}</strong></span>
                            {parsedData.stats.filamentUsedMm && (
                                <>
                                    <span>•</span>
                                    <span>Filament: <strong className="text-emerald-400 font-mono">{parsedData.stats.filamentUsedMm} mm</strong> ({parsedData.stats.filamentUsedCm3} cm³)</span>
                                </>
                            )}
                        </p>
                    </div>
                </div>

                {/* View Controls & Toggles */}
                <div className="flex items-center gap-2">
                    {/* View mode toggle */}
                    <div className="flex items-center rounded-lg bg-slate-800/80 p-0.5 border border-slate-700 text-xs">
                        <button
                            onClick={() => setViewMode('accumulative')}
                            className={`px-2.5 py-1 rounded-md transition-all ${viewMode === 'accumulative' ? 'bg-cyan-500 text-slate-950 font-semibold shadow' : 'text-slate-400 hover:text-white'}`}
                            title="Render all layers up to current layer"
                        >
                            All to Layer
                        </button>
                        <button
                            onClick={() => setViewMode('single')}
                            className={`px-2.5 py-1 rounded-md transition-all ${viewMode === 'single' ? 'bg-cyan-500 text-slate-950 font-semibold shadow' : 'text-slate-400 hover:text-white'}`}
                            title="Inspect current layer slice only"
                        >
                            Single Slice
                        </button>
                    </div>

                    {/* Travel Moves Toggle */}
                    <button
                        onClick={() => setShowTravelMoves(!showTravelMoves)}
                        className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs border transition-all ${
                            showTravelMoves 
                                ? 'bg-indigo-500/20 text-indigo-300 border-indigo-500/40 shadow-sm' 
                                : 'bg-slate-800 text-slate-400 border-slate-700 hover:text-slate-200'
                        }`}
                        title="Toggle visibility of non-extruding travel movements"
                    >
                        <Eye size={13} />
                        <span>Travel Moves</span>
                    </button>

                    {/* Camera Presets */}
                    <div className="flex items-center gap-1 bg-slate-800/80 p-0.5 rounded-lg border border-slate-700 text-xs">
                        <button
                            onClick={() => setCameraPreset('iso')}
                            className={`px-2 py-1 rounded ${cameraView === 'iso' ? 'bg-slate-700 text-cyan-400 font-medium' : 'text-slate-400 hover:text-white'}`}
                            title="Isometric 3D View"
                        >
                            3D
                        </button>
                        <button
                            onClick={() => setCameraPreset('top')}
                            className={`px-2 py-1 rounded ${cameraView === 'top' ? 'bg-slate-700 text-cyan-400 font-medium' : 'text-slate-400 hover:text-white'}`}
                            title="Top Down 2D Slice View"
                        >
                            Top
                        </button>
                        <button
                            onClick={() => setCameraPreset('front')}
                            className={`px-2 py-1 rounded ${cameraView === 'front' ? 'bg-slate-700 text-cyan-400 font-medium' : 'text-slate-400 hover:text-white'}`}
                            title="Front Elevation View"
                        >
                            Front
                        </button>
                    </div>

                    {/* Zoom / Reset */}
                    <button
                        onClick={() => handleZoom(0.85)}
                        className="p-1.5 rounded-lg bg-slate-800 text-slate-400 hover:text-white border border-slate-700"
                        title="Zoom In"
                    >
                        <ZoomIn size={14} />
                    </button>
                    <button
                        onClick={() => handleZoom(1.15)}
                        className="p-1.5 rounded-lg bg-slate-800 text-slate-400 hover:text-white border border-slate-700"
                        title="Zoom Out"
                    >
                        <ZoomOut size={14} />
                    </button>
                    <button
                        onClick={resetCamera}
                        className="p-1.5 rounded-lg bg-slate-800 text-slate-400 hover:text-white border border-slate-700"
                        title="Reset Camera"
                    >
                        <RotateCcw size={14} />
                    </button>
                </div>
            </div>

            {/* Main 3D Viewport Area */}
            <div ref={containerRef} className="relative flex-1 w-full min-h-[380px] bg-slate-950 overflow-hidden cursor-grab active:cursor-grabbing">
                <canvas ref={canvasRef} className="w-full h-full block" />

                {/* Floating HUD: Layer & Geometry Telemetry */}
                <div className="absolute top-4 left-4 pointer-events-none flex flex-col gap-2">
                    <div className="px-3.5 py-2.5 rounded-xl bg-slate-900/80 border border-slate-700/60 backdrop-blur-md shadow-lg flex flex-col gap-1">
                        <div className="flex items-center gap-2">
                            <span className="text-xs uppercase tracking-wider text-slate-400 font-semibold">Active Layer</span>
                            <span className="font-mono text-base font-bold text-cyan-400">
                                {currentLayer} <span className="text-slate-500 font-normal text-xs">/ {totalLayers}</span>
                            </span>
                        </div>
                        <div className="grid grid-cols-2 gap-x-4 gap-y-0.5 text-xs">
                            <span className="text-slate-400">Z Height:</span>
                            <span className="font-mono text-slate-200 font-medium text-right">{activeLayerData?.z ?? 0} mm</span>
                            
                            <span className="text-slate-400">Layer Height:</span>
                            <span className="font-mono text-slate-200 font-medium text-right">{activeLayerData?.height ?? 0.2} mm</span>

                            <span className="text-slate-400">Extrusions:</span>
                            <span className="font-mono text-slate-200 font-medium text-right">{activeLayerData?.extCount ?? 0} moves</span>

                            {showTravelMoves && (
                                <>
                                    <span className="text-slate-400">Travels:</span>
                                    <span className="font-mono text-slate-200 font-medium text-right">{activeLayerData?.travCount ?? 0} moves</span>
                                </>
                            )}
                        </div>
                    </div>

                    <div className="px-3 py-1.5 rounded-lg bg-slate-900/60 border border-slate-800 text-[11px] text-slate-400 backdrop-blur-sm">
                        Dimensions: <span className="font-mono text-slate-300">{parsedData.bounds.sizeX} × {parsedData.bounds.sizeY} × {parsedData.bounds.sizeZ} mm</span>
                    </div>
                </div>

                {/* Floating Feature Legend */}
                <div className="absolute bottom-4 right-4 pointer-events-none flex flex-col items-end gap-1.5">
                    <div className="p-3 rounded-xl bg-slate-900/85 border border-slate-800 backdrop-blur-md shadow-lg flex flex-col gap-1.5 max-w-[210px]">
                        <span className="text-[10px] uppercase tracking-wider text-slate-400 font-bold mb-0.5">Toolpath Features</span>
                        <div className="space-y-1">
                            {parsedData.featureTypes.map(f => (
                                <div key={f.key} className="flex items-center justify-between text-xs gap-3">
                                    <div className="flex items-center gap-2">
                                        <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: f.hex }} />
                                        <span className="text-slate-300 text-[11px] truncate">{f.label}</span>
                                    </div>
                                </div>
                            ))}
                            {showTravelMoves && (
                                <div className="flex items-center justify-between text-xs gap-3">
                                    <div className="flex items-center gap-2">
                                        <span className="w-2.5 h-2.5 rounded-full flex-shrink-0 bg-slate-500" />
                                        <span className="text-slate-300 text-[11px]">Travel Moves</span>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* Keyboard shortcut hint */}
                <div className="absolute bottom-4 left-4 pointer-events-none hidden md:block">
                    <span className="text-[10px] text-slate-500 bg-slate-900/50 px-2 py-1 rounded border border-slate-800 backdrop-blur-sm">
                        Tip: Use Left/Right Arrow keys to step layers • Spacebar to Play/Pause
                    </span>
                </div>
            </div>

            {/* Bottom Playback & Layer Scrubber Bar */}
            <div className="px-5 py-3.5 bg-slate-900 border-t border-slate-800 flex flex-col gap-2.5 z-10">
                {/* Layer Slider Track */}
                <div className="flex items-center gap-4">
                    <span className="text-xs font-mono text-slate-400 w-10 text-right">L 1</span>
                    <input
                        type="range"
                        min="1"
                        max={totalLayers}
                        value={currentLayer}
                        onChange={(e) => setCurrentLayer(parseInt(e.target.value, 10))}
                        className="flex-1 h-2 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-cyan-400 focus:outline-none focus:ring-2 focus:ring-cyan-500/40"
                    />
                    <span className="text-xs font-mono text-slate-400 w-14">L {totalLayers}</span>
                </div>

                {/* Player Controls & Speed Buttons */}
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        {/* Reset to Layer 1 */}
                        <button
                            onClick={() => { setIsPlaying(false); setCurrentLayer(1); }}
                            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700 transition-colors"
                            title="Reset to Layer 1"
                        >
                            <RotateCcw size={15} />
                        </button>

                        {/* Step Previous */}
                        <button
                            onClick={() => { setIsPlaying(false); setCurrentLayer(l => Math.max(1, l - 1)); }}
                            disabled={currentLayer <= 1}
                            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-300 hover:text-white border border-slate-700 transition-colors"
                            title="Previous Layer (Arrow Left)"
                        >
                            <ChevronLeft size={16} />
                        </button>

                        {/* Play / Pause */}
                        <button
                            onClick={() => {
                                if (currentLayer >= totalLayers) {
                                    setCurrentLayer(1);
                                    setIsPlaying(true);
                                } else {
                                    setIsPlaying(!isPlaying);
                                }
                            }}
                            className="px-4 py-2 rounded-lg bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-slate-950 font-bold flex items-center gap-2 shadow-lg shadow-cyan-500/20 transition-all active:scale-95"
                            title="Play/Pause Layer Animation (Spacebar)"
                        >
                            {isPlaying ? <Pause size={16} className="fill-current" /> : <Play size={16} className="fill-current" />}
                            <span className="text-xs uppercase tracking-wider">{isPlaying ? 'Pause' : currentLayer >= totalLayers ? 'Replay' : 'Animate'}</span>
                        </button>

                        {/* Step Next */}
                        <button
                            onClick={() => { setIsPlaying(false); setCurrentLayer(l => Math.min(totalLayers, l + 1)); }}
                            disabled={currentLayer >= totalLayers}
                            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-300 hover:text-white border border-slate-700 transition-colors"
                            title="Next Layer (Arrow Right)"
                        >
                            <ChevronRight size={16} />
                        </button>
                    </div>

                    {/* Speed Selector */}
                    <div className="flex items-center gap-1 bg-slate-800/90 p-1 rounded-lg border border-slate-700 text-xs">
                        <span className="text-[11px] text-slate-400 px-2 font-medium">Speed:</span>
                        {[0.5, 1, 2, 5, 10].map((spd) => (
                            <button
                                key={spd}
                                onClick={() => setPlaybackSpeed(spd)}
                                className={`px-2 py-0.5 rounded text-xs transition-colors ${
                                    playbackSpeed === spd 
                                        ? 'bg-cyan-500 text-slate-950 font-bold' 
                                        : 'text-slate-400 hover:text-white'
                                }`}
                            >
                                {spd}x
                            </button>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}
