import React from 'react';

interface CheckboxProps {
    checked: boolean;
    onChange: (checked: boolean) => void;
    disabled?: boolean;
    className?: string;
    id?: string;
    name?: string;
}

export const Checkbox: React.FC<CheckboxProps> = ({
    checked,
    onChange,
    disabled = false,
    className = '',
    id,
    name
}) => {
    return (
        <input
            id={id}
            name={name}
            type="checkbox"
            checked={checked}
            disabled={disabled}
            onChange={(event) => onChange(event.target.checked)}
            className={`themed-checkbox ${className}`.trim()}
        />
    );
};
