<script lang="ts">
	import '../app.css'
	import Button from "$lib/components/ui/button/Button.svelte";
	import { page } from '$app/state';
	import {Client, cacheExchange, fetchExchange, setContextClient} from '@urql/svelte';

	let { children } = $props();

	const client = new Client({
		url: 'http://localhost:8080/graphql',
		exchanges: [cacheExchange, fetchExchange],
		preferGetMethod: false
	});
	setContextClient(client);
</script>

<div class="mx-auto min-h-svh max-w-5xl p-6">
	<nav class="flex">
		<Button variant="secondary" href="/" disabled={page.url.pathname === '/'}>Home Page</Button>
		<Button variant="secondary" href="/test" disabled={page.url.pathname === '/test'}>Test Page</Button>
	</nav>
	<main class="space-y-8">{@render children()}</main>
</div>

<style>
</style>
