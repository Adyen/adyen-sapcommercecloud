import { Routes } from '@angular/router';

import { ComponentsPage } from './pages/components.page';
import { OverviewPage } from './pages/overview.page';
import { SetupPage } from './pages/setup/setup.page';

export const routes: Routes = [
  { path: '', component: OverviewPage, title: 'Overview', data: { breadcrumb: 'Overview' } },
  { path: 'setup', component: SetupPage, title: 'Connect Adyen', data: { breadcrumb: 'Connect Adyen' } },
  { path: 'components', component: ComponentsPage, title: 'Components', data: { breadcrumb: 'Components' } },
  { path: '**', redirectTo: '' },
];
