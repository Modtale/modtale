import React, { createContext, useContext } from 'react';
import { useLocation } from 'react-router-dom';

declare global {
    interface Window {
        INITIAL_DATA?: any;
    }
}

interface SSRContextType {
    initialData: any | null;
    initialPath: string;
}

const SSRContext = createContext<SSRContextType>({ initialData: null, initialPath: '/' });

export const SSRProvider: React.FC<{ data: any, initialPath?: string, children: React.ReactNode }> = ({ data, initialPath = '/', children }) => {
    return (
        <SSRContext.Provider value={{ initialData: data ?? null, initialPath }}>
            {children}
        </SSRContext.Provider>
    );
};

const normalizePath = (path: string) => (path || '/').replace(/\/$/, '') || '/';

export const useSSRData = () => {
    const { initialData, initialPath } = useContext(SSRContext);
    const location = useLocation();

    const isInitialRoute = normalizePath(location.pathname) === normalizePath(initialPath);

    return {
        initialData: isInitialRoute ? initialData : null,
        isInitialRoute,
    };
};
