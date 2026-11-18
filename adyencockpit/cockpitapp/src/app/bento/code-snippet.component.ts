import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';

import { BButton } from './button.component';

@Component({
  selector: 'b-code-snippet',
  imports: [BButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'b-code-snippet' },
  template: `
    <div class="b-code-snippet__action-bar b-typography b-typography--caption">
      <span>{{ language() }}</span>
      <button bButton type="button" variant="tertiary" [condensed]="true" [icon]="copied() ? 'check' : 'copy'"
              (click)="copy()">{{ copied() ? 'Copied' : 'Copy' }}</button>
    </div>
    <pre class="b-code-snippet__code-area"><code>{{ code() }}</code></pre>
  `,
})
export class BCodeSnippet {
  readonly code = input.required<string>();
  readonly language = input('text');

  protected readonly copied = signal(false);

  protected async copy(): Promise<void> {
    await navigator.clipboard.writeText(this.code());
    this.copied.set(true);
    setTimeout(() => this.copied.set(false), 2000);
  }
}
