import React, { createContext, useContext, useMemo } from 'react';

declare global {
    interface Window {
        INITIAL_DATA?: any;
    }
}

interface SSRContextType {
    initialData: any | null;
}

const SSRContext = createContext<SSRContextType>({ initialData: null });

export const SSRProvider: React.FC<{ data: any, children: React.ReactNode }> = ({ data, children }) => {
    const initialData = useMemo(() => {
        if (typeof window !== 'undefined' && window.INITIAL_DATA) {
            return window.INITIAL_DATA;
        }
        return data;
    }, [data]);

    return (
        <SSRContext.Provider value={{ initialData }}>
            {children}
        </SSRContext.Provider>
    );
};

export const useSSRData = () => useContext(SSRContext);