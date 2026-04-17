import type { PageLoad } from './$types';
import { fetchParkourRecords, fetchParkourRuns } from '$lib/api';

export const load = (async ({ fetch }) => {
	const [records, runs] = await Promise.all([
		fetchParkourRecords(fetch),
		fetchParkourRuns(fetch)
	]);

	return {
		records,
		runs
	};
}) satisfies PageLoad;
