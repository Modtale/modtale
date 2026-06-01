import React, { useState, useCallback } from 'react';
import Cropper from 'react-easy-crop';
import { X, Check } from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';

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
        if (!croppedAreaPixels) return;
        setIsProcessing(true);

        try {
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
            console.error('Failed to crop image', e);
            setIsProcessing(false);
        }
    };

    return (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200">
            <div className="bg-white dark:bg-slate-900 w-full max-w-3xl rounded-3xl shadow-2xl overflow-hidden border border-slate-200 dark:border-white/10 flex flex-col h-[80vh] md:h-[600px] animate-in zoom-in-95 duration-200">

                <div className="p-4 border-b border-slate-200 dark:border-white/5 flex justify-between items-center bg-slate-50 dark:bg-white/5">
                    <h2 className="text-lg font-black text-slate-900 dark:text-white">Crop Image</h2>
                    <button
                        onClick={onCancel}
                        disabled={isProcessing}
                        className="text-slate-400 hover:text-slate-600 dark:hover:text-white transition-colors disabled:opacity-50"
                    >
                        <X className="w-6 h-6" />
                    </button>
                </div>

                <div className="relative flex-1 bg-slate-950">
                    <Cropper
                        image={imageSrc}
                        crop={crop}
                        zoom={zoom}
                        aspect={aspect}
                        onCropChange={setCrop}
                        onCropComplete={onCropCompleteChange}
                        onZoomChange={setZoom}
                    />
                </div>

                <div className="p-4 border-t border-slate-200 dark:border-white/5 bg-slate-50 dark:bg-white/5 flex flex-col sm:flex-row items-center justify-between gap-4">
                    <div className="w-full sm:w-1/2 flex items-center gap-3">
                        <span className="text-xs font-bold text-slate-500 uppercase tracking-widest">Zoom</span>
                        <input
                            type="range"
                            value={zoom}
                            min={1}
                            max={3}
                            step={0.1}
                            aria-labelledby="Zoom"
                            onChange={(e) => setZoom(Number(e.target.value))}
                            className="w-full accent-modtale-accent h-2 bg-slate-200 dark:bg-white/10 rounded-lg appearance-none cursor-pointer"
                        />
                    </div>
                    <div className="flex items-center gap-3 w-full sm:w-auto">
                        <button
                            onClick={onCancel}
                            disabled={isProcessing}
                            className="flex-1 sm:flex-none px-6 py-2.5 rounded-xl font-bold text-sm bg-slate-200 dark:bg-white/10 text-slate-600 dark:text-slate-300 hover:bg-slate-300 dark:hover:bg-white/20 transition-all disabled:opacity-50"
                        >
                            Cancel
                        </button>
                        <button
                            onClick={handleSave}
                            disabled={isProcessing}
                            className="flex-1 sm:flex-none px-6 py-2.5 rounded-xl font-bold text-sm bg-modtale-accent text-white hover:bg-modtale-accentHover shadow-lg transition-all flex items-center justify-center gap-2 disabled:opacity-50"
                        >
                            {isProcessing ? <Spinner className="w-4 h-4 text-white" /> : <Check className="w-4 h-4" />}
                            Apply Crop
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};
