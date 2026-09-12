export const MAX_PROJECT_UPLOAD_BYTES = 100 * 1024 * 1024;
export const MAX_IMAGE_UPLOAD_BYTES = 10 * 1024 * 1024;

export const IMAGE_ACCEPT = 'image/png,image/jpeg,image/webp,image/gif,image/svg+xml';
export const IMAGE_FORMAT_LABEL = 'PNG, JPEG, WebP, GIF, or SVG';
export const IMAGE_DIMENSION_LABEL = 'max raster 3840 × 2160';
export const isSupportedImageFile = (file: File) => file.type === 'image/png'
    || file.type === 'image/jpeg'
    || file.type === 'image/webp'
    || file.type === 'image/gif'
    || file.type === 'image/svg+xml'
    || /\.(png|jpe?g|webp|gif|svg)$/i.test(file.name);

export const MAX_GALLERY_IMAGES = 20;
export const MAX_GALLERY_CAPTION_CHARACTERS = 240;
export const MAX_YOUTUBE_URL_CHARACTERS = 2048;

export const MAX_COMMENT_CHARACTERS = 5_000;
export const MAX_REPORT_DESCRIPTION_CHARACTERS = 5_000;

export const MAX_PROJECT_TITLE_CHARACTERS = 100;
export const MIN_PROJECT_SUMMARY_CHARACTERS = 10;
export const MAX_PROJECT_SUMMARY_CHARACTERS = 250;
export const MAX_PROJECT_DESCRIPTION_CHARACTERS = 50_000;
export const MIN_SLUG_CHARACTERS = 3;
export const MAX_SLUG_CHARACTERS = 50;

export const MIN_USERNAME_CHARACTERS = 3;
export const MAX_USERNAME_CHARACTERS = 30;
export const ACCOUNT_NAME_FORMAT_LABEL = 'letters, numbers, periods, underscores, and hyphens';
export const MIN_PASSWORD_CHARACTERS = 6;
export const MFA_CODE_LENGTH = 6;
export const MAX_PROFILE_BIO_CHARACTERS = 300;
export const MAX_ORGANIZATION_BIO_CHARACTERS = 5_000;
export const MIN_DISPLAY_NAME_CHARACTERS = 3;
export const MAX_DISPLAY_NAME_CHARACTERS = 30;
export const MAX_ROLE_NAME_CHARACTERS = 40;
