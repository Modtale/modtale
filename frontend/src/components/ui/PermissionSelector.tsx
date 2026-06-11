import React from 'react';
import { Checkbox } from '@/components/ui/Checkbox';
import type { Permission, PermissionDef, PermissionGroupDef } from '@/modules/permissions/permissions';

interface PermissionSelectorProps {
    groups: PermissionGroupDef[];
    selectedPermissions: Permission[];
    onChange: (permissions: Permission[]) => void;
    disabled?: boolean;
    variant?: 'card' | 'panel';
    className?: string;
}

export const PermissionSelector: React.FC<PermissionSelectorProps> = ({
                                                                          groups,
                                                                          selectedPermissions,
                                                                          onChange,
                                                                          disabled = false,
                                                                          variant = 'card',
                                                                          className = ''
                                                                      }) => {
    const togglePerm = (id: Permission) => {
        if (disabled) return;
        if (selectedPermissions.includes(id)) {
            onChange(selectedPermissions.filter(p => p !== id));
        } else {
            onChange([...selectedPermissions, id]);
        }
    };

    const toggleAllInGroup = (groupPermissions: PermissionDef[]) => {
        if (disabled) return;
        const groupIds = groupPermissions.map(p => p.id);
        const allSelected = groupIds.every(id => selectedPermissions.includes(id));

        if (allSelected) {
            onChange(selectedPermissions.filter(id => !groupIds.includes(id)));
        } else {
            onChange(Array.from(new Set([...selectedPermissions, ...groupIds])));
        }
    };

    const containerClasses = variant === 'panel'
        ? `columns-1 md:columns-2 lg:columns-3 gap-4 space-y-4 max-h-[420px] overflow-y-auto pr-2 pb-4 bg-slate-100/50 dark:bg-black/20 rounded-2xl p-4 ${className}`
        : `grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 ${className}`;

    const groupClasses = variant === 'panel'
        ? 'break-inside-avoid flex flex-col bg-white dark:bg-[#1a1f2e] rounded-xl border border-slate-200 dark:border-white/10 shadow-sm overflow-hidden'
        : 'bg-slate-50 dark:bg-white/5 rounded-xl border border-slate-200 dark:border-white/10 overflow-hidden self-start';

    const headerClasses = variant === 'panel'
        ? 'flex items-center justify-between bg-slate-50 dark:bg-white/5 px-3 py-2.5 border-b border-slate-200 dark:border-white/10'
        : 'flex items-center justify-between bg-slate-100 dark:bg-white/5 px-3 py-2';

    const headerTextClasses = variant === 'panel'
        ? 'text-xs font-bold text-slate-700 dark:text-slate-200 uppercase tracking-widest'
        : 'text-[10px] font-bold text-slate-500 uppercase tracking-widest';

    return (
        <div className={containerClasses}>
            {groups.map((group, idx) => (
                <div key={idx} className={groupClasses}>
                    <div className={headerClasses}>
                        <span className={headerTextClasses}>{group.group}</span>
                        {!disabled && (
                            <button
                                type="button"
                                onClick={() => toggleAllInGroup(group.permissions)}
                                className="text-[10px] text-modtale-accent hover:text-modtale-accentHover transition-colors font-bold uppercase tracking-wider"
                            >
                                Toggle
                            </button>
                        )}
                    </div>
                    <div className="p-2 space-y-0.5">
                        {group.permissions.map(perm => (
                            <label key={perm.id} className={`flex items-center gap-3 px-2.5 py-2 rounded-lg transition-colors group/label border border-transparent ${disabled ? 'opacity-50 cursor-not-allowed' : 'hover:bg-slate-50 dark:hover:bg-white/5 cursor-pointer hover:border-slate-100 dark:hover:border-white/5'}`}>
                                <Checkbox
                                    checked={selectedPermissions.includes(perm.id)}
                                    onChange={() => togglePerm(perm.id)}
                                    disabled={disabled}
                                    className={`w-4 h-4 shrink-0 text-modtale-accent border-slate-300 dark:border-slate-600 rounded focus:ring-modtale-accent focus:ring-offset-0 bg-white dark:bg-black/40 transition-all ${disabled ? '' : 'cursor-pointer'}`}
                                />
                                <span className="text-sm font-medium text-slate-600 dark:text-slate-300 group-hover/label:text-slate-900 dark:group-hover/label:text-white transition-colors select-none">{perm.label}</span>
                            </label>
                        ))}
                    </div>
                </div>
            ))}
        </div>
    );
};
