// @vitest-environment node
import { expect, it } from 'vitest';
import { readSettings } from '@/modules/project/utils/configSettings';
import { buildModpackOverrides, importModpackConfigs } from '@/modules/project/utils/modpackConfigs';

it('edits JSON values without rounding numbers or changing unrelated formatting', () => {
    const original = '{\n  "EnableXPLossOnDeath": true, "precise": 0.100000000000000001, "id": 9007199254740993, "nested": {"name":"before"}, "empty": null\n}';
    const doc = readSettings('config.json', original);
    expect(doc.save({})).toBe(original);
    expect(doc.settings[0]).toMatchObject({ label: 'XP loss on death', category: 'Death & penalties', type: 'boolean' });
    const result = doc.save({ '["EnableXPLossOnDeath"]': 'false', '["nested","name"]': 'after' });
    expect(result).toBe(original.replace('true', 'false').replace('before', 'after'));
    expect(() => doc.save({ '["id"]': 'NaN' })).toThrow('valid number');
});
it('keeps YAML comments, anchors and unrelated scalar values', () => {
    const text = '# settings\nenabled: true # keep\nid: 9007199254740993\nlabel: "before"\n';
    const doc = readSettings('config.yaml', text);
    expect(doc.save({ '["enabled"]': 'false' })).toBe(text.replace('true', 'false'));
    expect(doc.save({ '["label"]': 'yes' })).toContain('label: "yes"');
});
it('edits nested TOML settings and keeps large integers', () => {
    const doc = readSettings('config.toml', '[general]\nenabled = true\nid = 9223372036854775807\n');
    const result = doc.save({ '["general","enabled"]': 'false' });
    expect(result).toContain('enabled = false');
    expect(result).toContain('9223372036854775807');
});
it.each(['{"x":1,"x":2}', '{"x":true,}', '[]', '{broken}'])('rejects ambiguous or malformed JSON %s', text => {
    expect(() => readSettings('config.json', text)).toThrow();
});
it('bundles edited settings into a release and imports them for subsequent editing', async () => {
    const doc = readSettings('settings.json', '{"enabled":true,"count":5}');
    const text = doc.save({ '["count"]': '10' });
    const dependencies = [{ projectId: 'mod', projectTitle: 'Mod', versionNumber: '1' }];
    const bundle = await buildModpackOverrides({ dependencies, incompatibleProjectIds: [], versionNumber: '1', gameVersions: [], changelog: '', file: null, modConfigs: [{ id: 'config', projectId: 'mod', destination: 'Universe/mods/Mod', file: new File([text], 'settings.json') }] });
    const imported = await importModpackConfigs(bundle!, dependencies);
    const content = await imported.configs[0].file.text();
    expect(content).toBe('{"enabled":true,"count":10}');
    expect(readSettings('settings.json', content).settings[1].value).toBe('10');
});
