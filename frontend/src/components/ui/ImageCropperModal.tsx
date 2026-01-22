import React, { useState, useRef, useEffect, useCallback } from 'react';
import { X, Check, ZoomIn } from 'lucide-react';
import { getCroppedImg, createImage } from '../../utils/canvasUtils';

interface ImageCropperModalProps {
    imageSrc: string;
    onCancel: () => void;
    onCropComplete: (croppedFile: File) => void;
    aspect?: number;
}

export const ImageCropperModal: React.FC<ImageCropperModalProps> = ({
                                                                        imageSrc,
                                                                        onCancel,
                                                                        onCropComplete,
                                                                        aspect = 1
                                                                    }) => {
    const containerRef = useRef<HTMLDivElement>(null);
    const [imageObj, setImageObj] = useState<HTMLImageElement | null>(null);

    const [offset, setOffset] = useState({ x: 0, y: 0 });
    const [zoom, setZoom] = useState(1);

    const [baseScale, setBaseScale] = useState(0.1);
    const [coverZoom, setCoverZoom] = useState(1);
    const [minZoom, setMinZoom] = useState(1);
    const [maxZoom, setMaxZoom] = useState(1);

    const [isDragging, setIsDragging] = useState(false);
    const [dragStart, setDragStart] = useState({ x: 0, y: 0 });

    const [layout, setLayout] = useState({ cropBoxWidth: 0, cropBoxHeight: 0 });

    useEffect(() => {
        createImage(imageSrc).then((img) => {
            setImageObj(img);
        });
    }, [imageSrc]);

    const calculateLayout = useCallback(() => {
        if (!containerRef.current || !imageObj) return;

        const container = containerRef.current.getBoundingClientRect();

        const padding = 40;
        const availableWidth = container.width - padding;
        const availableHeight = container.height - padding;

        let cropBoxWidth, cropBoxHeight;

        if (availableWidth / availableHeight > aspect) {
            cropBoxHeight = availableHeight;
            cropBoxWidth = cropBoxHeight * aspect;
        } else {
            cropBoxWidth = availableWidth;
            cropBoxHeight = cropBoxWidth / aspect;
        }

        const scaleW = cropBoxWidth / imageObj.naturalWidth;
        const scaleH = cropBoxHeight / imageObj.naturalHeight;

        const fitScale = Math.min(scaleW, scaleH);
        const fillScale = Math.max(scaleW, scaleH);

        const calculatedCoverZoom = fillScale / fitScale;

        setBaseScale(fitScale);
        setCoverZoom(calculatedCoverZoom);

        setMinZoom(1);
        setMaxZoom(Math.max(5, calculatedCoverZoom * 2));

        setZoom(calculatedCoverZoom);
        setOffset({ x: 0, y: 0 });

        return { cropBoxWidth, cropBoxHeight };

    }, [imageObj, aspect]);

    useEffect(() => {
        const dims = calculateLayout();
        if (dims) setLayout(dims);

        window.addEventListener('resize', () => {
            const d = calculateLayout();
            if (d) setLayout(d);
        });
    }, [calculateLayout]);

    const clampOffset = (x: number, y: number, currentZoom: number) => {
        if (!imageObj) return { x, y };

        const currentScale = baseScale * currentZoom;
        const renderW = imageObj.naturalWidth * currentScale;
        const renderH = imageObj.naturalHeight * currentScale;

        let limitX = 0;
        if (renderW > layout.cropBoxWidth) {
            limitX = (renderW - layout.cropBoxWidth) / 2;
        }

        let limitY = 0;
        if (renderH > layout.cropBoxHeight) {
            limitY = (renderH - layout.cropBoxHeight) / 2;
        }

        return {
            x: Math.max(-limitX, Math.min(limitX, x)),
            y: Math.max(-limitY, Math.min(limitY, y))
        };
    };

    const getSnappedZoom = (z: number) => {
        const SNAP_THRESHOLD = 0.15;
        if (Math.abs(z - coverZoom) < SNAP_THRESHOLD) {
            return coverZoom;
        }
        return z;
    };

    const handleWheel = (e: React.WheelEvent) => {
        e.stopPropagation();
        const delta = -e.deltaY * 0.005;
        let newZoom = Math.max(minZoom, Math.min(maxZoom, zoom + delta));

        newZoom = getSnappedZoom(newZoom);

        const clamped = clampOffset(offset.x, offset.y, newZoom);
        setZoom(newZoom);
        setOffset(clamped);
    };

    const handlePointerDown = (e: React.PointerEvent) => {
        e.preventDefault();
        setIsDragging(true);
        setDragStart({ x: e.clientX - offset.x, y: e.clientY - offset.y });
    };

    const handlePointerMove = (e: React.PointerEvent) => {
        if (!isDragging) return;
        e.preventDefault();

        const rawX = e.clientX - dragStart.x;
        const rawY = e.clientY - dragStart.y;

        const clamped = clampOffset(rawX, rawY, zoom);
        setOffset(clamped);
    };

    const handlePointerUp = () => {
        setIsDragging(false);
    };

    const handleSave = async () => {
        if (!imageObj) return;

        try {
            const finalScale = baseScale * zoom;

            const file = await getCroppedImg(
                imageSrc,
                { x: offset.x, y: offset.y, zoom: finalScale },
                { width: layout.cropBoxWidth, height: layout.cropBoxHeight },
                "cropped.png"
            );
            onCropComplete(file);
        } catch (e) {
            console.error(e);
        }
    };

    return (
        <div className="fixed inset-0 z-[200] flex items-center justify-center bg-black/90 backdrop-blur-sm p-4 animate-in fade-in duration-200"
             onPointerUp={handlePointerUp}
             onPointerLeave={handlePointerUp}
        >
            <div className="bg-white dark:bg-modtale-card w-full max-w-lg rounded-xl overflow-hidden shadow-2xl flex flex-col h-[80dvh] md:h-[600px]">

                <div className="p-4 border-b border-slate-200 dark:border-white/10 flex justify-between items-center bg-white dark:bg-modtale-card z-10">
                    <h3 className="font-black text-lg text-slate-900 dark:text-white">Crop Image</h3>
                    <button onClick={onCancel} className="text-slate-500 hover:text-red-500 transition-colors">
                        <X className="w-6 h-6" />
                    </button>
                </div>

                <div
                    ref={containerRef}
                    className="relative flex-1 bg-slate-900 overflow-hidden cursor-move flex items-center justify-center select-none"
                    onWheel={handleWheel}
                    onPointerDown={handlePointerDown}
                    onPointerMove={handlePointerMove}
                    style={{ touchAction: 'none' }}
                >
                    {imageObj && layout.cropBoxWidth > 0 && (
                        <>
                            <div
                                style={{
                                    width: layout.cropBoxWidth,
                                    height: layout.cropBoxHeight,
                                    outline: '2000px solid rgba(0,0,0,0.5)',
                                    zIndex: 20,
                                    pointerEvents: 'none',
                                    border: '2px solid rgba(255,255,255,0.5)',
                                    position: 'relative'
                                }}
                            >
                                <div className="absolute top-1/3 left-0 w-full h-px bg-white/40 shadow-sm" />
                                <div className="absolute top-2/3 left-0 w-full h-px bg-white/40 shadow-sm" />
                                <div className="absolute top-0 left-1/3 w-px h-full bg-white/40 shadow-sm" />
                                <div className="absolute top-0 left-2/3 w-px h-full bg-white/40 shadow-sm" />
                            </div>

                            <div
                                style={{
                                    position: 'absolute',
                                    transform: `translate(${offset.x}px, ${offset.y}px) scale(${baseScale * zoom})`,
                                    transformOrigin: 'center center',
                                    width: imageObj.naturalWidth,
                                    height: imageObj.naturalHeight,
                                    zIndex: 10,
                                }}
                            >
                                <img
                                    src={imageSrc}
                                    alt=""
                                    className="w-full h-full pointer-events-none block"
                                    draggable={false}
                                />
                            </div>
                        </>
                    )}
                </div>

                <div className="p-6 bg-white dark:bg-modtale-card border-t border-slate-200 dark:border-white/10 space-y-4">
                    <div className="flex items-center gap-4">
                        <ZoomIn className="w-5 h-5 text-slate-400" />
                        <input
                            type="range"
                            value={zoom}
                            min={minZoom}
                            max={maxZoom}
                            step={0.01}
                            onChange={(e) => {
                                const val = Number(e.target.value);
                                const newZoom = getSnappedZoom(val);
                                setZoom(newZoom);
                                setOffset(clampOffset(offset.x, offset.y, newZoom));
                            }}
                            className="w-full h-2 bg-slate-200 dark:bg-white/10 rounded-lg appearance-none cursor-pointer accent-modtale-accent"
                        />
                    </div>
                    <div className="flex justify-end gap-3">
                        <button onClick={onCancel} className="px-4 py-2 rounded-lg font-bold text-slate-500 hover:bg-slate-100 dark:hover:bg-white/10 transition-colors">Cancel</button>
                        <button onClick={handleSave} className="px-6 py-2 bg-modtale-accent hover:bg-modtale-accentHover text-white rounded-lg font-bold flex items-center gap-2 shadow-lg shadow-modtale-accent/20 transition-all active:scale-95">
                            <Check className="w-4 h-4" /> Save Crop
                        </button>
                    </div>
                </div>

            </div>
        </div>
    );
};