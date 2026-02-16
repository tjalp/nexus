<script lang="ts" module>
    import {tv, type VariantProps} from "tailwind-variants";
    import type {WithElementRef} from "$lib/utils";
    import {cn} from "$lib/utils";
    import type {HTMLAnchorAttributes, HTMLButtonAttributes} from "svelte/elements";

    export const buttonVariants = tv({
        base: [
            // Layout & Sizing
            "w-full gap-1.5 relative",
            // Interaction
            "cursor-pointer select-none text-center",
            // Border
            "border-2 border-solid border-[#242424]",
            // Pseudo-element for 3D effect
            "after:block after:absolute after:top-0 after:left-0 after:w-full after:h-full after:z-1",
            "after:mix-blend-hard-light after:pointer-events-none",
            // Default 3D shadow (unpressed)
            "after:inset-shadow-[0_-6px_0_0_rgb(104,104,104),2px_2px_0_0_rgba(178,178,178,0.5),-2px_-8px_0_0_rgba(153,153,153,0.5)]",
            // Active state (pressed down)
            "active:mt-1.5",
            "active:after:inset-shadow-[2px_2px_0_0_rgba(178,178,178,0.5),-2px_-2px_0_0_rgba(153,153,153,0.5)]",
            "active:after:bg-[#0000001a]",
            // Hover state
            "hover:not-active:not-disabled:after:bg-[#ffffff1a]",
            // Transitions
            "duration-100 after:duration-100 hover:after:duration-100",
            // Disabled state (pressed down appearance)
            "disabled:mt-1.5 disabled:opacity-60 disabled:cursor-not-allowed",
            "disabled:after:inset-shadow-[2px_2px_0_0_rgba(178,178,178,0.5),-2px_-2px_0_0_rgba(153,153,153,0.5)]",
            "disabled:after:bg-[#0000001a]",
            "disabled:hover:after:bg-[#0000001a]"
        ],
        variants: {
            variant: {
                default: "bg-primary text-primary-foreground",
                secondary: "bg-secondary text-secondary-foreground",
                destructive: "bg-destructive text-destructive-foreground",
            }
        },
        defaultVariants: {
            variant: "default",
        }
    });

    export type ButtonVariant = VariantProps<typeof buttonVariants>["variant"];

    export type ButtonProps = WithElementRef<HTMLButtonAttributes> &
        WithElementRef<HTMLAnchorAttributes> & {
        variant?: ButtonVariant;
    };
</script>

<script lang="ts">
    let {
        class: className,
        variant = "default",
        ref = $bindable(null),
        href = undefined,
        type = "button",
        disabled,
        children,
        ...restProps
    }: ButtonProps = $props();
</script>

{#if href}
    <a
            bind:this={ref}
            data-slot="button"
            class={cn(buttonVariants({ variant }), className)}
            href={disabled ? undefined : href}
            aria-disabled={disabled}
            role={disabled ? "link" : undefined}
            tabindex={disabled ? -1 : undefined}
            {...restProps}
    >
        <span class="flex w-[calc(100%+4px)] h-[calc(100%+4px)] -m-0.5 gap-1.5 text-center items-center justify-center duration-100
            {disabled ? 'pt-3.5 px-2.5 pb-2 -mt-2 h-[calc(100%+10px)]' : 'pt-2 px-2.5 pb-3.5 active:pt-3.5 active:pb-2 active:-mt-2 active:h-[calc(100%+10px)]'}"
        >
            {@render children?.()}
        </span>
    </a>
{:else}
    <button
            bind:this={ref}
            data-slot="button"
            class={cn(buttonVariants({ variant }), className)}
            {type}
            {disabled}
            {...restProps}
    >
        <span class="flex w-[calc(100%+4px)] h-[calc(100%+4px)] -m-0.5 gap-1.5 text-center items-center justify-center duration-100
            {disabled ? 'pt-3.5 px-2.5 pb-2 -mt-2 h-[calc(100%+10px)]' : 'pt-2 px-2.5 pb-3.5 active:pt-3.5 active:pb-2 active:-mt-2 active:h-[calc(100%+10px)]'}"
        >
            {@render children?.()}
        </span>
    </button>
{/if}
