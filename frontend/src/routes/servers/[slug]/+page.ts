import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';
import { demoServers, getServerBySlug } from '$lib/data/mock';

export const load = (({ params }) => {
	const server = getServerBySlug(params.slug);
	if (!server) {
		throw error(404, 'Server not found');
	}

	return {
		server,
		peers: demoServers.filter((candidate) => candidate.slug !== params.slug)
	};
}) satisfies PageLoad;
