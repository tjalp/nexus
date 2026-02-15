<script lang="ts" module>
    import {tv, type VariantProps} from "tailwind-variants";
    import type {WithElementRef} from "$lib/utils";
    import {cn} from "$lib/utils";
    import type {HTMLAnchorAttributes, HTMLButtonAttributes} from "svelte/elements";

    export const buttonVariants = tv({
        base: "w-full gap-1.5 relative cursor-pointer select-none text-center border-2 border-solid border-[#242424] after:block after:absolute after:top-0 after:left-0 after:w-full after:h-full after:z-1 after:mix-blend-hard-light after:pointer-events-none active:mt-1.5 active:after:inset-shadow-[2px_2px_0_0_rgba(178,178,178,0.5),-2px_-2px_0_0_rgba(153,153,153,0.5)] active:after:bg-[#0000001a] after:inset-shadow-[0_-6px_0_0_rgb(104,104,104),2px_2px_0_0_rgba(178,178,178,0.5),-2px_-8px_0_0_rgba(153,153,153,0.5)] hover:not-active:after:bg-[#ffffff1a] hover:after:duration-100 duration-100 after:duration-100",
        variants: {
            variant: {
                default: "bg-primary text-primary-foreground",
                secondary: "bg-secondary text-secondary-foreground",
                destructive: "bg-destructive text-destructive-foreground",
                // outline: "bg-transparent border-input hover:bg-accent hover:text-accent-foreground",
            }
        },
        defaultVariants: {
            variant: "default"
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
        <span class="flex w-[calc(100%+4px)] h-[calc(100%+4px)] -m-0.5 gap-1.5 pt-2 px-2.5 pb-3.5 text-center items-center justify-center
            active:pt-3.5 active:pb-2 active:-mt-2 active:h-[calc(100%+10px)] duration-100"
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
        <span class="flex w-[calc(100%+4px)] h-[calc(100%+4px)] -m-0.5 gap-1.5 pt-2 px-2.5 pb-3.5 text-center items-center justify-center
            active:pt-3.5 active:pb-2 active:-mt-2 active:h-[calc(100%+10px)] duration-100"
        >
            {@render children?.()}
        </span>
    </button>
{/if}

<!--    after:inset-shadow-[2px_2px_0_0_rgba(178, 178, 178, 0.5)] after:inset-shadow-[-2px_-2px_0_0_rgba(153, 153, 153, 0.5)]-->
<!--<button class="w-full gap-1.5 relative cursor-pointer select-none text-center text-white border-2 border-solid border-[#242424] bg-[#48494a]-->
<!--    after:block after:absolute after:top-0 after:left-0 after:w-full after:h-full after:z-1 after:mix-blend-hard-light after:pointer-events-none-->
<!--    active:mt-1.5 active:after:inset-shadow-[2px_2px_0_0_rgba(178,178,178,0.5),-2px_-2px_0_0_rgba(153,153,153,0.5)] active:after:bg-[#0000001a]-->
<!--    after:inset-shadow-[0_-6px_0_0_rgb(104,104,104),2px_2px_0_0_rgba(178,178,178,0.5),-2px_-8px_0_0_rgba(153,153,153,0.5)]-->
<!--    hover:not-active:after:bg-[#ffffff1a] hover:after:duration-100-->
<!--    duration-100 after:duration-100"-->
<!--&gt;-->
<!--    <span class="flex w-[calc(100%+4px)] h-[calc(100%+4px)] -m-0.5 gap-1.5 pt-2 px-2.5 pb-3.5 text-center items-center justify-center-->
<!--        active:pt-3.5 active:pb-2 active:-mt-2 active:h-[calc(100%+10px)] duration-100"-->
<!--    >-->
<!--        Push me-->
<!--    </span>-->
<!--</button>-->