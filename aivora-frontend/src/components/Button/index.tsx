import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

const styles = cva(
  'inline-flex items-center justify-center rounded-lg px-4 py-2 text-sm font-medium transition-colors disabled:opacity-50 disabled:pointer-events-none',
  {
    variants: {
      variant: {
        default: 'bg-emerald-700 text-white hover:bg-emerald-800',
        outline: 'border border-slate-500 bg-transparent text-emerald-800 hover:bg-emerald-50',
      },
    },
    defaultVariants: { variant: 'default' },
  },
);
type Props = React.ComponentProps<'button'> & VariantProps<typeof styles> & { asChild?: boolean };
export function Button({ className, variant, asChild = false, ...props }: Props) {
  const Component = asChild ? Slot : 'button';
  return <Component className={twMerge(clsx(styles({ variant }), className))} {...props} />;
}
