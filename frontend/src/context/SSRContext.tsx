import React, { createContext, useContext } from 'react';

interface SSRContextType {
    initialData: any | null;
}

const SSRContext = createContext<SSRContextType>({ initialData: null });

export const SSRProvider: React.FC<{ data: any, children: React.ReactNode }> = ({ data, children }) => {
    return (
        <SSRContext.Provider value={{ initialData: data }}>
            {children}
        </SSRContext.Provider>
    );
};

export const useSSRData = () => useContext(SSRContext);