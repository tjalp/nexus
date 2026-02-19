<script lang="ts">
	import '../app.css'
	import Button from "$lib/components/ui/button/Button.svelte";
	import {page} from '$app/state';
	import {setContextClient} from '@urql/svelte';
	import {urqlClient} from "$lib/urql-client";
	import {authStore, isAuthenticated} from "$lib/auth";

	let { children } = $props();

	setContextClient(urqlClient);
</script>

<div class="mx-auto min-h-svh max-w-5xl p-6">
	<nav class="flex">
		<Button variant="secondary" href="/" disabled={page.url.pathname === '/'}>Home Page</Button>
		<Button variant="secondary" href="/test" disabled={page.url.pathname === '/test'}>Test Page</Button>
		{#if $isAuthenticated}
			<Button variant="destructive" onclick={() => authStore.clearAuth()}>Logout</Button>
		{:else}
			<Button variant="secondary" href="/login" disabled={page.url.pathname === '/login'}>Login</Button>
		{/if}
	</nav>
	<main class="space-y-8">{@render children()}</main>
</div>

<style>
</style>
