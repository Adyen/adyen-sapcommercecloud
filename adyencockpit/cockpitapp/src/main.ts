import { provideHttpClient, withFetch } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter, withHashLocation } from '@angular/router';

import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';

bootstrapApplication(AppComponent, {
  providers: [
    // State lives in signals, so change detection needs no zone patching of browser APIs.
    provideZonelessChangeDetection(),
    provideHttpClient(withFetch()),
    // Hash routing keeps every deep link inside index.html, so the cockpit needs no server-side
    // forwarding rule and stays indifferent to the context path it is mounted under.
    provideRouter(routes, withHashLocation()),
  ],
}).catch((err) => console.error(err));
