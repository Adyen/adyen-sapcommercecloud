import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';

// console.log("react app " + Date.now());
//
// document.addEventListener('DOMContentLoaded', () => {
//     console.log("dom loaded " + Date.now());
//
//     setTimeout(function (){
//         console.log("timeout " + Date.now());
//         let reactRoots = document.getElementsByClassName('adyen-react');
//
//         if(reactRoots.length == 1) {
//             const root = ReactDOM.createRoot(
//                 reactRoots[0] as HTMLElement
//             );
//             root.render(
//                 <React.StrictMode>
//                     <App />
//                 </React.StrictMode>
//             );
//         } else {
//             console.error("Incorrect root elements count: " + reactRoots.length)
//         }
//     }, 10000)
//
// })

    // let interval = setInterval(function () {
    //     let reactRoots = document.getElementsByClassName('adyen-react');
    //     console.log("check");
    //     if (reactRoots.length == 1) {
    //         clearInterval(interval);
    //         console.log("initialized");
    //
    //         const root = ReactDOM.createRoot(
    //             reactRoots[0] as HTMLElement
    //         );
    //         root.render(
    //             <React.StrictMode>
    //                 <App/>
    //             </React.StrictMode>
    //         );
    //     }
    // }, 500);

let reactRoots = document.getElementsByClassName('adyen-react');
console.log("check");
if (reactRoots.length == 1) {
    console.log("initialized");

    const root = ReactDOM.createRoot(
        reactRoots[0] as HTMLElement
    );
    root.render(
        <React.StrictMode>
            <App/>
        </React.StrictMode>
    );
} else {
    console.log("init error");
}


