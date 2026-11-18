import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Generic 16px glyphs drawn for the cockpit. Adyen's own icon set ships with the Customer Area and
 * is not ours to redistribute; these stand in until Adyen supplies it, keeping the names stable so
 * the swap touches this file only.
 */
const PATHS: Record<string, string> = {
  check: 'M3.5 8.5l3 3 6-7',
  close: 'M4 4l8 8M12 4l-8 8',
  plus: 'M8 3v10M3 8h10',
  'chevron-down': 'M4 6l4 4 4-4',
  'chevron-up': 'M4 10l4-4 4 4',
  'chevron-right': 'M6 4l4 4-4 4',
  search: 'M7 12.5a5.5 5.5 0 1 0 0-11 5.5 5.5 0 0 0 0 11zM11 11l3.5 3.5',
  copy: 'M5.5 5.5h7v8h-7zM3.5 10.5v-8h7',
  info: 'M8 14.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM8 7.5v4M8 5v.01',
  success: 'M8 14.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM5.25 8.25l2 2 3.5-4',
  warning: 'M8 2l6.5 11.5h-13zM8 6.5v3.5M8 12v.01',
  critical: 'M8 14.5a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM8 4.75v4M8 11v.01',
  home: 'M2.5 7.5L8 2.5l5.5 5M4 6.5v7h8v-7',
  key: 'M10 9.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM7.5 8.5l-5 5M4 12l1.5 1.5M5.5 10.5L7 12',
  webhook: 'M5 11.5a2.5 2.5 0 1 1 0-5M11 11.5a2.5 2.5 0 1 0 0-5M5.5 9h5',
  grid: 'M2.5 2.5h4.5v4.5h-4.5zM9 2.5h4.5v4.5H9zM2.5 9h4.5v4.5h-4.5zM9 9h4.5v4.5H9z',
  store: 'M2.5 6.5l1-4h9l1 4M2.5 6.5h11M3.5 6.5v7h9v-7M6.5 13.5v-4h3v4',
  external: 'M9 2.5h4.5V7M13.5 2.5L7.5 8.5M11.5 9.5v4h-9v-9h4',
};

@Component({
  selector: 'b-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-icon', 'aria-hidden': 'true' },
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 16 16" fill="none"
         stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
      <path [attr.d]="path()" />
    </svg>
  `,
  styles: [':host { display: inline-flex; flex-shrink: 0; }'],
})
export class BIcon {
  readonly name = input.required<string>();
  readonly size = input(16);

  protected readonly path = computed(() => PATHS[this.name()] ?? '');
}
