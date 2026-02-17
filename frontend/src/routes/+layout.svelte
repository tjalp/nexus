<script lang="ts">
	import '../app.css'
	import Button from "$lib/components/ui/button/Button.svelte";

	let { children } = $props();
</script>

<div class="mx-auto min-h-svh max-w-5xl p-6">
	<nav class="flex">
		<Button variant="secondary" href="/">Home Page</Button>
		<Button variant="secondary" href="/test">Test Page</Button>
	</nav>
	<main class="space-y-8">{@render children()}</main>
</div>

<style>
</style>
