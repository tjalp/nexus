import type { PageLoad } from './$types';
import { demoServers, privilegedRoles } from '$lib/data/mock';

export const load = (() => {
	return {
		servers: demoServers,
		privilegedRoles
	};
}) satisfies PageLoad;
