import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, map, of } from 'rxjs';

import { BIcon } from './bento';
import { CockpitApiService } from './core/cockpit-api.service';

interface NavItem {
  label: string;
  icon: string;
  path: string;
}

interface NavSection {
  title: string;
  items: NavItem[];
}

/** The Customer Area frame: top bar split at the sidebar edge, navigation drawer, then the page. */
@Component({
  selector: 'adyen-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="app">
      <div class="app__header">
        <div class="app__sidebar-header">
          <div class="sidebar-header">
            <div class="app-sidebar-account">
              <div class="app-sidebar-account__image" aria-hidden="true">A</div>
              <div class="app-sidebar-account__data">
                <span class="app-sidebar-account__name">Adyen for SAP Commerce</span>
                <span class="app-sidebar-account__type">Backoffice</span>
              </div>
            </div>
          </div>
        </div>
        <div class="app__page-header">
          <header class="app-header">
            <nav class="app-header-section" aria-label="Breadcrumb">
              <ol class="app-header-breadcrumbs" style="list-style: none; margin: 0; padding: 0;">
                <li class="app-header-breadcrumbs__item b-typography b-typography--caption">Adyen</li>
                <li class="app-header-breadcrumbs__icon" aria-hidden="true"><b-icon name="chevron-right" /></li>
                <li class="app-header-breadcrumbs__item app-header-breadcrumbs__item--active b-typography b-typography--caption b-typography--caption-stronger"
                    aria-current="page">{{ breadcrumb() }}</li>
              </ol>
            </nav>
            <div class="app-header-section">
              @if (user(); as name) {
                <span class="b-avatar" role="img" [attr.aria-label]="'Signed in as ' + name">{{ name.charAt(0) }}</span>
              }
            </div>
          </header>
        </div>
      </div>

      <div class="b-drawer app__content">
        <aside class="b-drawer__drawer" aria-label="Main navigation">
          <div class="app-sidebar__navigation">
            @for (section of navigation; track section.title) {
              <nav class="b-navigation-menu" [attr.aria-label]="section.title">
                <div class="b-navigation-menu__header">
                  <p class="b-typography b-navigation-menu__title b-typography--caption b-typography--caption-stronger">{{ section.title }}</p>
                </div>
                <ul class="b-navigation-menu__content">
                  @for (item of section.items; track item.path) {
                    <li class="b-navigation-menu-item"
                        routerLinkActive="b-navigation-menu-item--selected"
                        [routerLinkActiveOptions]="{ exact: true }">
                      <a class="b-navigation-menu-item__link" [routerLink]="item.path" ariaCurrentWhenActive="page">
                        <span class="b-navigation-menu-item__icon"><b-icon [name]="item.icon" /></span>
                        <span class="b-typography b-typography--body b-typography--body-stronger">{{ item.label }}</span>
                      </a>
                    </li>
                  }
                </ul>
              </nav>
            }
          </div>
        </aside>
        <main class="b-drawer__page">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class AppComponent {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  // Only routes that exist belong here; a menu entry without a page is a link to nowhere.
  protected readonly navigation: NavSection[] = [
    {
      title: 'Pages',
      items: [
        { label: 'Overview', icon: 'home', path: '/' },
        { label: 'Connect Adyen', icon: 'key', path: '/setup' },
      ],
    },
    { title: 'Design system', items: [{ label: 'Components', icon: 'grid', path: '/components' }] },
  ];

  // toSignal rethrows a stream error on every read, which would abort rendering of the whole frame.
  // The page reports a failed bootstrap; the shell just goes without the avatar.
  protected readonly user = toSignal(
    inject(CockpitApiService).bootstrap$.pipe(
      map((b) => b.user),
      catchError(() => of(null)),
    ),
    { initialValue: null },
  );

  private readonly navigated = toSignal(
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)),
  );

  protected readonly breadcrumb = computed(() => {
    this.navigated();
    let current = this.route.snapshot;
    while (current.firstChild) {
      current = current.firstChild;
    }
    return (current.data['breadcrumb'] as string | undefined) ?? '';
  });
}
