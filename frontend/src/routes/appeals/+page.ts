import type { PageLoad } from './$types';
import { demoAppeals, demoProfiles } from '$lib/data/mock';

export const load = (() => {
	return {
		appeals: demoAppeals,
		profiles: demoProfiles
	};
}) satisfies PageLoad;
