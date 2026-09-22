import type { Project, ProjectVersion } from '@/types';

// Local, deterministic content: fixtures never request project or media data.
export const PROJECT_LOADING_IMAGE = 'data:image/svg+xml,%3Csvg%20xmlns=%22http://www.w3.org/2000/svg%22%20width=%221600%22%20height=%22900%22/%3E';
export const PROJECT_LOADING_PROSE = `## About this project

Explore new possibilities with a collection of features built for your world. This guide covers the essentials and helps you get started with the project.

## Getting started

Download the latest compatible release and add it to your game. Configure the available options to suit the way you play.

- Choose the version that matches your game.
- Install the project and its required dependencies.
- Start your world and explore the available features.

## Configuration

The default settings provide a starting point. Adjust individual options to customize your experience and share your world with friends.`;
export const PROJECT_LOADING_VERSIONS: ProjectVersion[] = ['1.2.0', '1.1.0', '1.0.0'].map((versionNumber, index) => ({
    id: `loading-version-${index}`, versionNumber, gameVersion: '2026.01.17', gameVersions: ['2026.01.17'],
    fileUrl: '', downloadCount: 1200, releaseDate: '2026-01-17T00:00:00Z', channel: 'RELEASE', dependencies: [],
    changelog: '### Changes\n\nImproved compatibility and updated configuration options.\n\n- Added new features for your world.\n- Improved the installation experience.'
}));
export const PROJECT_LOADING_DATA: Project = {
    id: 'loading-project', slug: 'loading-project', title: 'Project title', author: 'Project creator', authorId: '',
    description: 'Explore new possibilities with a collection of features built for your world. Discover new content and customize the way you play.', about: PROJECT_LOADING_PROSE,
    imageUrl: '', classification: 'PLUGIN', license: 'MIT', tags: ['Adventure', 'Utility'],
    downloadCount: 12000, favoriteCount: 120, updatedAt: '2026-01-17T00:00:00Z', status: 'PUBLISHED',
    versions: PROJECT_LOADING_VERSIONS, galleryImages: [], hmWikiEnabled: false
};
export const loadingNoop = () => {};
