export type ParkourRecord = {
	course: string;
	player: string;
	timeMs: number;
	date: string;
	video?: string;
};

export type ParkourRun = {
	id: string;
	player: string;
	course: string;
	timeMs: number;
	status: 'verified' | 'pending' | 'rejected';
	submittedAt: string;
	device?: string;
	notes?: string;
};

export const sampleRecords: ParkourRecord[] = [
	{
		course: 'Skyline Sprint',
		player: 'Nova',
		timeMs: 42133,
		date: '2024-12-06T12:00:00Z',
		video: 'https://example.com/video/skyline'
	},
	{ course: 'Helix Drop', player: 'Iris', timeMs: 38995, date: '2024-12-04T18:10:00Z' },
	{ course: 'Frostbite', player: 'Rook', timeMs: 50221, date: '2024-12-03T10:40:00Z' },
	{ course: 'Carbon Canyon', player: 'Solace', timeMs: 44777, date: '2024-12-02T20:15:00Z' },
	{ course: 'Metro Glide', player: 'Kyto', timeMs: 43120, date: '2024-12-01T08:45:00Z' }
];

export const sampleRuns: ParkourRun[] = [
	{
		id: 'RUN-001',
		player: 'Nova',
		course: 'Skyline Sprint',
		timeMs: 42133,
		status: 'verified',
		submittedAt: '2024-12-06T12:00:00Z',
		device: 'KB+M',
		notes: 'Clean run, no skips.'
	},
	{
		id: 'RUN-002',
		player: 'Iris',
		course: 'Helix Drop',
		timeMs: 38995,
		status: 'verified',
		submittedAt: '2024-12-04T18:10:00Z',
		device: 'Controller'
	},
	{
		id: 'RUN-003',
		player: 'Rook',
		course: 'Frostbite',
		timeMs: 50221,
		status: 'pending',
		submittedAt: '2024-12-03T10:40:00Z',
		notes: 'Awaiting demo review.'
	},
	{
		id: 'RUN-004',
		player: 'Solace',
		course: 'Carbon Canyon',
		timeMs: 44777,
		status: 'verified',
		submittedAt: '2024-12-02T20:15:00Z'
	},
	{
		id: 'RUN-005',
		player: 'Kyto',
		course: 'Metro Glide',
		timeMs: 43120,
		status: 'verified',
		submittedAt: '2024-12-01T08:45:00Z',
		device: 'KB+M'
	},
	{
		id: 'RUN-006',
		player: 'Astra',
		course: 'Aurora Leap',
		timeMs: 46215,
		status: 'pending',
		submittedAt: '2024-12-05T13:12:00Z'
	},
	{
		id: 'RUN-007',
		player: 'Vex',
		course: 'Metro Glide',
		timeMs: 47201,
		status: 'rejected',
		submittedAt: '2024-12-05T15:35:00Z',
		notes: 'Video missing last checkpoint.'
	},
	{
		id: 'RUN-008',
		player: 'Nova',
		course: 'Helix Drop',
		timeMs: 39501,
		status: 'verified',
		submittedAt: '2024-12-06T09:02:00Z',
		device: 'KB+M'
	}
];
