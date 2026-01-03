export const createImage = (url: string): Promise<HTMLImageElement> =>
    new Promise((resolve, reject) => {
        const image = new Image();
        image.addEventListener('load', () => resolve(image));
        image.addEventListener('error', (error) => reject(error));
        image.setAttribute('crossOrigin', 'anonymous');
        image.src = url;
    });

export async function getCroppedImg(
    imageSrc: string,
    crop: { x: number; y: number; zoom: number },
    targetSize: { width: number; height: number },
    fileName: string
): Promise<File> {
    const image = await createImage(imageSrc);
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');

    if (!ctx) throw new Error('No 2d context');

    canvas.width = targetSize.width;
    canvas.height = targetSize.height;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    ctx.translate(canvas.width / 2, canvas.height / 2);
    ctx.translate(crop.x, crop.y);
    ctx.scale(crop.zoom, crop.zoom);
    ctx.drawImage(image, -image.width / 2, -image.height / 2);

    return new Promise((resolve, reject) => {
        canvas.toBlob((blob) => {
            if (!blob) {
                reject(new Error('Canvas is empty'));
                return;
            }
            resolve(new File([blob], fileName, { type: 'image/png' }));
        }, 'image/png');
    });
}