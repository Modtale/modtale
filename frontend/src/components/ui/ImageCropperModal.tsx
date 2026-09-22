import React, { useState, useCallback, useId } from 'react';
import Cropper from 'react-easy-crop';
import { X, Check, Crop, Minus, Plus } from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';
import { isGifImage } from '@/utils/images';
import { ModalPortal } from '@/components/ui/ModalPortal';

interface ImageCropperModalProps {
    imageSrc: string;
    sourceFile?: File | null;
    aspect: number;
    onCancel: () => void;
    onCropComplete: (file: File) => void;
}

const createImage = (url: string): Promise<HTMLImageElement> =>
    new Promise((resolve, reject) => {
        const image = new Image();
        image.addEventListener('load', () => resolve(image));
        image.addEventListener('error', (error) => reject(error));
        image.setAttribute('crossOrigin', 'anonymous');
        image.src = url;
    });

const parseNumericLength = (value: string | null): number | null => {
    if (!value) return null;
    const match = value.trim().match(/^([0-9]*\.?[0-9]+)/);
    return match ? Number(match[1]) : null;
};

export const ImageCropperModal: React.FC<ImageCropperModalProps> = ({
                                                                        imageSrc,
                                                                        sourceFile,
                                                                        aspect,
                                                                        onCancel,
                                                                        onCropComplete
                                                                    }) => {
    const titleId = useId();
    const zoomId = useId();
    const isGif = isGifImage(imageSrc, sourceFile);
    const [error, setError] = useState<string | null>(null);
    const [crop, setCrop] = useState({ x: 0, y: 0 });
    const [zoom, setZoom] = useState(1);
    const [croppedAreaPixels, setCroppedAreaPixels] = useState<any>(null);
    const [isProcessing, setIsProcessing] = useState(false);

    const onCropCompleteChange = useCallback((croppedArea: any, croppedAreaPixels: any) => {
        setCroppedAreaPixels(croppedAreaPixels);
    }, []);

    const resolveSourceMimeType = useCallback(async (): Promise<string> => {
        if (sourceFile?.type?.startsWith('image/')) {
            return sourceFile.type;
        }
        if (sourceFile?.name?.toLowerCase().endsWith('.svg')) {
            return 'image/svg+xml';
        }

        if (imageSrc.startsWith('data:image/')) {
            const match = imageSrc.match(/^data:(image\/[a-zA-Z0-9.+-]+);/);
            if (match?.[1]) return match[1];
        }

        try {
            const response = await fetch(imageSrc);
            const blob = await response.blob();
            if (blob.type) return blob.type;
        } catch (e) {
            console.warn('Could not determine original MIME type, falling back to PNG');
        }

        return 'image/png';
    }, [imageSrc, sourceFile]);

    const buildCroppedSvgFile = useCallback(
        async (image: HTMLImageElement, cropPixels: { x: number; y: number; width: number; height: number }) => {
            const svgText = sourceFile ? await sourceFile.text() : await (await fetch(imageSrc)).text();
            const parser = new DOMParser();
            const doc = parser.parseFromString(svgText, 'image/svg+xml');
            const svg = doc.documentElement;

            if (!svg || svg.tagName.toLowerCase() !== 'svg') {
                throw new Error('Invalid SVG source');
            }

            const viewBoxAttr = svg.getAttribute('viewBox');
            let baseX = 0;
            let baseY = 0;
            let baseWidth = image.naturalWidth;
            let baseHeight = image.naturalHeight;

            if (viewBoxAttr) {
                const parts = viewBoxAttr.trim().split(/[\s,]+/).map(Number);
                if (parts.length === 4 && parts.every((n) => Number.isFinite(n))) {
                    baseX = parts[0];
                    baseY = parts[1];
                    baseWidth = parts[2];
                    baseHeight = parts[3];
                }
            } else {
                const widthAttr = parseNumericLength(svg.getAttribute('width'));
                const heightAttr = parseNumericLength(svg.getAttribute('height'));
                if (widthAttr && heightAttr) {
                    baseWidth = widthAttr;
                    baseHeight = heightAttr;
                }
            }

            const scaleX = baseWidth / image.naturalWidth;
            const scaleY = baseHeight / image.naturalHeight;

            const cropX = baseX + cropPixels.x * scaleX;
            const cropY = baseY + cropPixels.y * scaleY;
            const cropWidth = cropPixels.width * scaleX;
            const cropHeight = cropPixels.height * scaleY;

            svg.setAttribute('viewBox', `${cropX} ${cropY} ${cropWidth} ${cropHeight}`);
            svg.setAttribute('width', `${cropWidth}`);
            svg.setAttribute('height', `${cropHeight}`);

            const serialized = new XMLSerializer().serializeToString(doc);
            return new File([serialized], 'cropped-image.svg', { type: 'image/svg+xml' });
        },
        [imageSrc, sourceFile]
    );

    const handleSave = async () => {
        if (!isGif && !croppedAreaPixels) return;
        setError(null);
        setIsProcessing(true);

        try {
            if (isGif) {
                // Canvas export only retains one frame. Upload the original animation.
                const file = sourceFile ?? new File([await (await fetch(imageSrc)).blob()], 'image.gif', { type: 'image/gif' });
                const uploadFile = /\.gif$/i.test(file.name)
                    ? file
                    : new File([file], `${file.name.replace(/\.[^.]+$/, '') || 'image'}.gif`, { type: 'image/gif' });
                onCropComplete(uploadFile);
                return;
            }
            const image = await createImage(imageSrc);
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');

            if (!ctx) throw new Error('No 2d context available');

            canvas.width = croppedAreaPixels.width;
            canvas.height = croppedAreaPixels.height;

            ctx.drawImage(
                image,
                croppedAreaPixels.x,
                croppedAreaPixels.y,
                croppedAreaPixels.width,
                croppedAreaPixels.height,
                0,
                0,
                croppedAreaPixels.width,
                croppedAreaPixels.height
            );

            const sourceMimeType = await resolveSourceMimeType();
            if (sourceMimeType === 'image/svg+xml') {
                const svgFile = await buildCroppedSvgFile(image, croppedAreaPixels);
                onCropComplete(svgFile);
                return;
            }

            let mimeType = sourceMimeType;

            const supportedTypes = ['image/jpeg', 'image/png', 'image/webp'];
            if (!supportedTypes.includes(mimeType)) {
                mimeType = 'image/png';
            }

            canvas.toBlob(
                (blob) => {
                    if (!blob) {
                        setIsProcessing(false);
                        return;
                    }
                    const extension = mimeType.split('/')[1];
                    const file = new File([blob], `cropped-image.${extension}`, { type: mimeType });
                    onCropComplete(file);
                },
                mimeType,
                0.92
            );
        } catch (e) {
            console.error('Failed to prepare image', e);
            setError('Could not prepare this image. Please try another file.');
            setIsProcessing(false);
        }
    };

    return (
        <ModalPortal>
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200" onClick={(e) => e.stopPropagation()}>
            <div role="dialog" aria-modal="true" aria-labelledby={titleId} className="bg-white dark:bg-modtale-card w-full max-w-2xl max-h-[calc(100dvh-2rem)] rounded-2xl shadow-2xl overflow-y-auto border border-slate-200 dark:border-white/10 flex flex-col animate-in zoom-in-95 duration-200">
                <div className="px-6 py-4 border-b border-slate-200 dark:border-white/10 flex justify-between items-center gap-4 shrink-0">
                    <div className="flex items-center gap-3">
                        <Crop className="w-5 h-5 text-modtale-accent shrink-0" />
                        <h2 id={titleId} className="!m-0 text-xl font-black leading-none text-slate-900 dark:text-white">{isGif ? 'Preview GIF' : 'Crop Image'}</h2>
                    </div>
                    <button onClick={onCancel} disabled={isProcessing} aria-label="Close image editor"
                        className="w-10 h-10 shrink-0 inline-flex items-center justify-center rounded-xl border border-slate-200 dark:border-white/10 text-slate-500 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-white/5 hover:text-slate-900 dark:hover:text-white transition-colors disabled:opacity-50">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <div className="p-4 sm:p-6 space-y-5">
                    <div className="relative h-[clamp(180px,45dvh,400px)] overflow-hidden rounded-xl border border-slate-200 dark:border-white/10 bg-slate-100 dark:bg-slate-950">
                        {isGif ? (
                            <div className="absolute inset-4 flex items-center justify-center" style={{ containerType: 'size' }}>
                                <img src={imageSrc} alt="Animated image preview" className="max-h-full max-w-full object-cover rounded-lg" style={{ width: `min(100cqw, ${aspect * 100}cqh)`, height: `min(${100 / aspect}cqw, 100cqh)` }} />
                            </div>
                        ) : <Cropper
                            image={imageSrc}
                            crop={crop}
                            zoom={zoom}
                            aspect={aspect}
                            showGrid={false}
                            onCropChange={setCrop}
                            onCropComplete={onCropCompleteChange}
                            onZoomChange={setZoom}
                        />}
                    </div>
                    {isGif ? <p className="!m-0 text-sm text-slate-500 dark:text-slate-400">Your GIF stays animated and is centered to fit.</p> : (
                        <div className="space-y-3">
                            <div className="flex items-center justify-between text-sm">
                                <label htmlFor={zoomId} className="font-bold text-slate-700 dark:text-slate-200">Zoom</label>
                                <output htmlFor={zoomId} className="tabular-nums text-slate-500 dark:text-slate-400">{Math.round(zoom * 100)}%</output>
                            </div>
                            <div className="flex items-center gap-4">
                                <button aria-label="Zoom out" onClick={() => setZoom(value => Math.max(1, value - 0.1))} disabled={zoom <= 1 || isProcessing}
                                    className="w-9 h-9 shrink-0 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5 disabled:opacity-40 transition-colors"><Minus className="w-4 h-4" /></button>
                                <input id={zoomId} type="range" value={zoom} min={1} max={3} step={0.01} disabled={isProcessing}
                                    onChange={(e) => setZoom(Number(e.target.value))} className="themed-range h-4 w-full min-w-0 cursor-pointer" />
                                <button aria-label="Zoom in" onClick={() => setZoom(value => Math.min(3, value + 0.1))} disabled={zoom >= 3 || isProcessing}
                                    className="w-9 h-9 shrink-0 flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5 disabled:opacity-40 transition-colors"><Plus className="w-4 h-4" /></button>
                            </div>
                        </div>
                    )}
                    {error && <p role="alert" className="text-sm text-red-500">{error}</p>}
                </div>
                <div className="px-4 sm:px-6 py-4 border-t border-slate-200 dark:border-white/10 flex items-center justify-end gap-3 shrink-0">
                    <button onClick={onCancel} disabled={isProcessing}
                        className="flex-1 sm:flex-none px-5 h-11 rounded-xl border border-slate-200 dark:border-white/10 font-bold text-sm text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-white/5 transition-colors disabled:opacity-50">Cancel</button>
                    <button onClick={handleSave} disabled={isProcessing || (!isGif && !croppedAreaPixels)}
                        className="flex-1 sm:flex-none px-5 h-11 rounded-xl font-bold text-sm whitespace-nowrap bg-modtale-accent text-white hover:bg-modtale-accentHover transition-colors flex items-center justify-center gap-2 disabled:opacity-50">
                        {isProcessing ? <Spinner className="w-4 h-4 text-white" /> : <Check className="w-4 h-4" />}
                        {isGif ? 'Use GIF' : 'Apply Crop'}
                    </button>
                </div>
            </div>
        </div>
        </ModalPortal>
    );
};
