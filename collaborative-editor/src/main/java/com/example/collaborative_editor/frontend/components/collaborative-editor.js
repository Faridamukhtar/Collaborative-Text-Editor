import { PolymerElement } from '@polymer/polymer/polymer-element.js';

class CollaborativeEditor extends PolymerElement {
    static get is() {
        return 'collaborative-editor';
    }
}

customElements.define(CollaborativeEditor.is, CollaborativeEditor);