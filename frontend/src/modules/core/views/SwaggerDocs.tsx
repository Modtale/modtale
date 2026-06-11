import React, { useEffect } from 'react';
import { BACKEND_URL } from '@/utils/api';

const SWAGGER_UI_URL = `${BACKEND_URL}/api/v1/docs/swagger`;

export const SwaggerDocs: React.FC = () => {
    useEffect(() => {
        window.location.replace(SWAGGER_UI_URL);
    }, []);

    return null;
};
