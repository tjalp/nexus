export type PublicProfile = {
	id: string;
	username: string;
	displayName: string;
	role: 'admin' | 'engineer' | 'operator' | 'moderator' | 'member' | 'guest';
	timezone: string;
	reputation: number;
	lastSeen: string;
	primaryServer: string;
	pronouns?: string;
	tags: string[];
};

export type AttachmentSet = {
	notes: string[];
	securityFlags: string[];
	publicSummary: string;
	sensitiveSummary: string;
	connections: {
		discord?: string;
		github?: string;
	};
};

export type Appeal = {
	id: string;
	username: string;
	displayName: string;
	status: 'open' | 'review' | 'closed';
	type: 'ban' | 'mute' | 'warning';
	submittedAt: string;
	reference: string;
	summary: string;
	channel: 'web' | 'email' | 'ingame';
};

export type PlayerSession = {
	username: string;
	displayName: string;
	status: 'online' | 'idle' | 'recent';
	ping: number;
	lastSeen: string;
	server: string;
};

export type ServerActivityPoint = {
	label: string;
	players: number;
};

export type ConsoleEntry = {
	at: string;
	level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG';
	message: string;
};

export type ServerNode = {
	id: string;
	name: string;
	slug: string;
	status: 'online' | 'degraded' | 'offline';
	region: string;
	playerCount: number;
	maxPlayers: number;
	latencyMs: number;
	uptime: string;
	version: string;
	address: string;
	tps: number;
	cpu: number;
	memory: number;
	tags: string[];
	activity: ServerActivityPoint[];
	players: PlayerSession[];
	consoleFeed: ConsoleEntry[];
};

export const privilegedRoles = ['admin', 'engineer', 'operator'] as const;
export const privilegedUsers = ['nova', 'iris', 'warden'] as const;

export const demoProfiles: Array<PublicProfile & { attachments: AttachmentSet }> = [
	{
		id: 'USR-2041',
		username: 'nova',
		displayName: 'Nova',
		role: 'admin',
		timezone: 'UTC+1',
		reputation: 98,
		lastSeen: '2m ago',
		primaryServer: 'Atlas Core',
		pronouns: 'they/them',
		tags: ['security', 'ops', 'access:root'],
		attachments: {
			notes: ['Rotates credentials weekly', 'Maintains EU cluster config'],
			securityFlags: ['Hardware MFA enforced', 'SSH keys rotated'],
			publicSummary: 'Ops lead for the EU cluster. Contact for escalations.',
			sensitiveSummary: 'Trusted for live patching and hotfix deploys.',
			connections: { discord: 'nova#0421', github: 'nova-ops' }
		}
	},
	{
		id: 'USR-2305',
		username: 'iris',
		displayName: 'Iris',
		role: 'moderator',
		timezone: 'UTC-5',
		reputation: 86,
		lastSeen: '8m ago',
		primaryServer: 'Helix',
		pronouns: 'she/her',
		tags: ['appeals', 'support'],
		attachments: {
			notes: ['Handles evening appeal triage', 'Knows Dutch and English'],
			securityFlags: ['Web sessions expire after 15m idle'],
			publicSummary: 'Community moderator focused on appeals and onboarding.',
			sensitiveSummary: 'Pending elevated access for server restarts.',
			connections: { discord: 'iris.mod', github: 'iris-m' }
		}
	},
	{
		id: 'USR-2449',
		username: 'rook',
		displayName: 'Rook',
		role: 'member',
		timezone: 'UTC',
		reputation: 71,
		lastSeen: '34m ago',
		primaryServer: 'Atlas Core',
		tags: ['builder', 'redstone'],
		attachments: {
			notes: ['Prefers chill servers', 'Pending ticket about region transfer'],
			securityFlags: ['Requires username review before promotions'],
			publicSummary: 'Casual player focused on builds.',
			sensitiveSummary: 'No elevated access granted.',
			connections: { discord: 'rookie', github: 'rook-dev' }
		}
	}
];

export const demoAppeals: Appeal[] = [
	{
		id: 'APL-1042',
		username: 'rook',
		displayName: 'Rook',
		status: 'review',
		type: 'ban',
		submittedAt: '2024-12-04T15:22:00Z',
		reference: 'PN-88231',
		channel: 'web',
		summary: 'Appealing a temp-ban for suspected griefing on Helix. Provided replay link.'
	},
	{
		id: 'APL-1046',
		username: 'solace',
		displayName: 'Solace',
		status: 'open',
		type: 'warning',
		submittedAt: '2024-12-05T11:05:00Z',
		reference: 'PN-88302',
		channel: 'ingame',
		summary: 'Requesting removal of outdated warning after appeal review.'
	},
	{
		id: 'APL-1051',
		username: 'kyto',
		displayName: 'Kyto',
		status: 'closed',
		type: 'mute',
		submittedAt: '2024-12-03T19:40:00Z',
		reference: 'PN-88188',
		channel: 'email',
		summary: 'Mute lifted. Awaiting confirmation and follow-up note.'
	}
];

export const demoServers: ServerNode[] = [
	{
		id: 'alpha-core',
		name: 'Atlas Core',
		slug: 'atlas-core',
		status: 'online',
		region: 'EU-West',
		playerCount: 42,
		maxPlayers: 120,
		latencyMs: 32,
		uptime: '18h 12m',
		version: '1.20.4',
		address: 'atlas.nexus.gg',
		tps: 19.8,
		cpu: 54,
		memory: 63,
		tags: ['survival', 'vanilla+', 'stable'],
		activity: [
			{ label: '06:00', players: 21 },
			{ label: '09:00', players: 44 },
			{ label: '12:00', players: 51 },
			{ label: '15:00', players: 58 },
			{ label: '18:00', players: 72 },
			{ label: '21:00', players: 65 }
		],
		players: [
			{
				username: 'nova',
				displayName: 'Nova',
				status: 'online',
				ping: 38,
				lastSeen: 'now',
				server: 'Atlas Core'
			},
			{
				username: 'rook',
				displayName: 'Rook',
				status: 'idle',
				ping: 59,
				lastSeen: '3m ago',
				server: 'Atlas Core'
			},
			{
				username: 'lumen',
				displayName: 'Lumen',
				status: 'recent',
				ping: 48,
				lastSeen: '12m ago',
				server: 'Atlas Core'
			}
		],
		consoleFeed: [
			{ at: '09:14:02', level: 'INFO', message: '[Core] Saved world in 412ms' },
			{ at: '09:14:15', level: 'INFO', message: '[Auth] New session minted for nova' },
			{ at: '09:15:02', level: 'WARN', message: '[Proxy] High latency spike detected (182ms)' },
			{ at: '09:16:44', level: 'INFO', message: '[WorldGuard] Region atlas_market reloaded' },
			{ at: '09:17:12', level: 'INFO', message: '[ChatBridge] Forwarded 12 messages' }
		]
	},
	{
		id: 'helix-edge',
		name: 'Helix',
		slug: 'helix',
		status: 'degraded',
		region: 'US-East',
		playerCount: 27,
		maxPlayers: 90,
		latencyMs: 66,
		uptime: '9h 44m',
		version: '1.20.4',
		address: 'helix.nexus.gg',
		tps: 18.6,
		cpu: 68,
		memory: 71,
		tags: ['modded', 'events'],
		activity: [
			{ label: '06:00', players: 18 },
			{ label: '09:00', players: 22 },
			{ label: '12:00', players: 35 },
			{ label: '15:00', players: 42 },
			{ label: '18:00', players: 48 },
			{ label: '21:00', players: 37 }
		],
		players: [
			{
				username: 'iris',
				displayName: 'Iris',
				status: 'online',
				ping: 44,
				lastSeen: 'now',
				server: 'Helix'
			},
			{
				username: 'solace',
				displayName: 'Solace',
				status: 'recent',
				ping: 78,
				lastSeen: '9m ago',
				server: 'Helix'
			},
			{
				username: 'kyto',
				displayName: 'Kyto',
				status: 'idle',
				ping: 91,
				lastSeen: '1m ago',
				server: 'Helix'
			}
		],
		consoleFeed: [
			{ at: '09:14:02', level: 'INFO', message: '[Helix] Starting scheduled restart window check' },
			{
				at: '09:14:55',
				level: 'WARN',
				message: '[Helix] Plugin latencies above threshold (avg 127ms)'
			},
			{ at: '09:15:33', level: 'INFO', message: '[Helix] Cleared 12 stale cache entries' },
			{
				at: '09:16:01',
				level: 'ERROR',
				message: '[Helix] Failed to reach metrics backend, retrying...'
			},
			{ at: '09:16:45', level: 'INFO', message: '[Helix] Metrics recovered' }
		]
	},
	{
		id: 'warden-lab',
		name: 'Warden Lab',
		slug: 'warden',
		status: 'offline',
		region: 'EU-North',
		playerCount: 0,
		maxPlayers: 30,
		latencyMs: 0,
		uptime: 'offline',
		version: '1.21 experimental',
		address: 'warden.nexus.gg',
		tps: 0,
		cpu: 0,
		memory: 0,
		tags: ['snapshot', 'experiments', 'whitelisted'],
		activity: [
			{ label: '06:00', players: 0 },
			{ label: '09:00', players: 6 },
			{ label: '12:00', players: 12 },
			{ label: '15:00', players: 8 },
			{ label: '18:00', players: 10 },
			{ label: '21:00', players: 0 }
		],
		players: [],
		consoleFeed: [
			{ at: '07:21:02', level: 'INFO', message: '[Warden] Applying snapshot config' },
			{
				at: '07:25:11',
				level: 'WARN',
				message: '[Warden] Memory utilization exceeded 80% during regen'
			},
			{
				at: '07:27:00',
				level: 'ERROR',
				message: '[Warden] Shut down due to missing datapack checksum'
			}
		]
	}
];

export function getServerBySlug(slug: string) {
	return demoServers.find((server) => server.slug === slug);
}
