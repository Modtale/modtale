export type BeaconRange = '24h' | '7d' | '30d';
export interface BeaconActivity {
    bucketStart: string;
    servers: number;
    players: number;
}
export interface BeaconStats {
    slug: string;
    displayName: string;
    activeServers: number;
    activePlayers: number;
    recordServers: number;
    recordPlayers: number;
    lastHeartbeatAt: string | null;
    activity: BeaconActivity[];
}
