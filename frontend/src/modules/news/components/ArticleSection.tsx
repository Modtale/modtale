import type { ReactNode } from 'react';

export function ArticleSection({ id, children }: { id: string; children: ReactNode }) {
    return <section id={id}>{children}</section>;
}
