import type { ParkourRecord, ParkourRun } from './data/parkour';
import { sampleRecords, sampleRuns } from './data/parkour';

const API_BASE = 'http://localhost:8080/api';

async function safeJson<T>(promise: Promise<Response>, fallback: T): Promise<T> {
	try {
		const res = await promise;
		if (!res.ok) return fallback;
		return (await res.json()) as T;
	} catch (error) {
		console.warn('Falling back to sample data', error);
		return fallback;
	}
}

export function fetchParkourRecords(fetcher = fetch) {
	return safeJson<ParkourRecord[]>(fetcher(`${API_BASE}/parkour/records`), sampleRecords);
}

export function fetchParkourRuns(fetcher = fetch) {
	return safeJson<ParkourRun[]>(fetcher(`${API_BASE}/parkour/runs`), sampleRuns);
}
