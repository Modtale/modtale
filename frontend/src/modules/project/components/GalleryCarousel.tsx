import React from 'react';

import { GalleryCarouselViewer } from './GalleryCarouselViewer';
import type { GalleryImageInput } from '../utils/galleryImages';

interface GalleryCarouselProps {
    images?: GalleryImageInput[];
    captions?: Record<string, string>;
    title: string;
}

export const GalleryCarousel: React.FC<GalleryCarouselProps> = ({ images = [], captions = {}, title }) => (
    <GalleryCarouselViewer images={images} captions={captions} title={title} />
);
