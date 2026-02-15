<script lang="ts">
	import '../app.css'

	let { children } = $props();
</script>

<div class="mx-auto min-h-svh">
	<main>{@render children()}</main>
</div>

<style>
</style>
