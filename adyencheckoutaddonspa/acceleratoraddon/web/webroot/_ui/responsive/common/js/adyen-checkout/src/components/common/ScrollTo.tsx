import React from "react";


export class ScrollHere extends React.Component<{}, null> {
    private scrollHereClass: string = "adyen-scroll-here";

    private scrollToFirstInstance() {
        let elementsByClassName = document.getElementsByClassName(this.scrollHereClass);
        if (elementsByClassName && elementsByClassName.length > 0) {
            let newTop = this.getNewTopPosition(elementsByClassName);
            newTop = newTop - 10;
            window.scrollBy({top: newTop, behavior: 'smooth'});
        }
    }

    private getNewTopPosition(elementsByClassName: HTMLCollectionOf<Element>) {
        let newTop: number = undefined;

        for (let i = 0; i < elementsByClassName.length; i++) {
            let elementTop = elementsByClassName.item(i).getBoundingClientRect().top;
            if (i === 0) {
                newTop = elementTop;
            }
            if (elementTop < newTop) {
                newTop = elementTop;
            }
        }
        return newTop;
    }

    componentDidMount() {
        this.scrollToFirstInstance()
    }

    render() {
        return <div className={this.scrollHereClass}></div>
    }
}