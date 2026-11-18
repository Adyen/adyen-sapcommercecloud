import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Page title, one line of context, and the page's primary actions projected on the right. */
@Component({
  selector: 'b-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-header' },
  template: `
    <div class="b-header__titles">
      <h1 class="b-typography b-typography--title-l">{{ title() }}</h1>
      @if (description()) {
        <p class="b-typography b-header__description b-typography--body">{{ description() }}</p>
      }
    </div>
    <div class="b-header__actions"><ng-content /></div>
  `,
})
export class BPageHeader {
  readonly title = input.required<string>();
  readonly description = input<string | null>(null);
}
