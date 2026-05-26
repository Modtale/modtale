import React, { useState, useCallback } from 'react';
import Cropper from 'react-easy-crop';
import { X, Check } from 'lucide-react';
import { Spinner } from '@/components/ui/Spinner';

interface ImageCropperModalProps {
    imageSrc: string;
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

export const ImageCropperModal: React.FC<ImageCropperModalProps> = ({
                                                                        imageSrc,
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
        if (imageSrc.startsWith('data:image/')) {
            const match = imageSrc.match(/^data:(image\/[a-zA-Z0-9.+-]+);/);
            if (match?.[1]) return match[1];
        }

        if (imageSrc.startsWith('blob:')) {
            return 'image/jpeg';
        }

        try {
            const response = await fetch(imageSrc);
            const blob = await response.blob();
            if (blob.type) return blob.type;
        } catch (e) {
            console.warn('Could not determine original MIME type, falling back to JPEG');
        }

        return 'image/jpeg';
    }, [imageSrc]);

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

            let mimeType = await resolveSourceMimeType();

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
