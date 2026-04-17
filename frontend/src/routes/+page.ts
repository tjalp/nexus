import type { PageLoad } from './$types';
import {
	demoAppeals,
	demoProfiles,
	demoServers,
	privilegedRoles,
	privilegedUsers
} from '$lib/data/mock';

export const load = (() => {
	return {
		profiles: demoProfiles,
		appeals: demoAppeals,
		servers: demoServers,
		privilegedRoles,
		privilegedUsers
	};
}) satisfies PageLoad;
