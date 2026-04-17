import type { paths } from './openapi/types';
import { sampleRecords, sampleRuns } from './data/parkour';

const API_BASE = 'http://localhost:8080/api';

type RecordsResponse =
	paths['/api/parkour/records']['get']['responses'][200]['content']['application/json'];
type RunsResponse =
	paths['/api/parkour/runs']['get']['responses'][200]['content']['application/json'];

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
	return safeJson<RecordsResponse>(fetcher(`${API_BASE}/parkour/records`), sampleRecords);
}

export function fetchParkourRuns(fetcher = fetch) {
	return safeJson<RunsResponse>(fetcher(`${API_BASE}/parkour/runs`), sampleRuns);
}
