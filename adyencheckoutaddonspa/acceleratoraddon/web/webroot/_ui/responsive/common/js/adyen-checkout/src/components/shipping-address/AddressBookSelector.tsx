import React from "react";
import {connect} from "react-redux";
import {AppState} from "../../reducers/rootReducer";
import {AddressModel} from "../../reducers/types";
import {isNotEmpty} from "../../util/stringUtil";
import './AddressBookSelectorStyle.scss'

import {translationsStore} from "../../store/translationsStore";

interface Props {
    closeModal: () => void,
    onSelectAddress: (address: AddressModel) => void
}

interface StoreProps {
    addressBook: AddressModel[]
}


interface AddressBookSelectorProps extends Props, StoreProps {
}

class AddressBookSelector extends React.Component<AddressBookSelectorProps, null> {


    private async handleSelectAddress(address: AddressModel) {
        this.props.onSelectAddress(address)
        this.props.closeModal()
    }


    private renderAddressBookEntries(): React.JSX.Element[] {
        let result: React.JSX.Element[] = []

        this.props.addressBook.forEach((address, index) => {
            result.push(
                <div className={"addressBookSelector_modal_addressesContainer_addressItem"} key={index}>
                    <strong>{isNotEmpty(address.title) ? address.title + '\xa0' : ''}{address.firstName}&nbsp;{address.lastName}&nbsp;</strong>
                    <br/>
                    {address.line1}&nbsp;{address.line2}
                    <br/>
                    {address.city}{isNotEmpty(address.region)? '\xa0' + address.region : ''}
                    <br/>
                    {address.country}&nbsp;{address.postalCode}
                    <br/>
                    <button
                        className={"addressBookSelector_modal_addressesContainer_addressItem_button btn btn-primary btn-block"}
                        onClick={() => this.handleSelectAddress(address)}>{translationsStore.get("checkout.multi.deliveryAddress.useThisAddress")}
                    </button>
                </div>)
        })
        return result
    }

    render() {
        return (
            <>
                <div className={"addressBookSelector_backdrop"} onClick={() => this.props.closeModal()}/>
                <dialog open className={"addressBookSelector_modal"}>
                <span className={"addressBookSelector_modal_close glyphicon glyphicon-remove"}
                      onClick={() => this.props.closeModal()}/>
                    <div
                        className={"addressBookSelector_modal_header"}>{translationsStore.get("checkout.multi.deliveryAddress.savedAddresses")}</div>
                    <div className={"addressBookSelector_modal_addressesContainer"}>
                        {this.renderAddressBookEntries()}
                    </div>
                </dialog>
            </>
        )
    }
}

const mapStateToProps = (state: AppState): StoreProps => ({
    addressBook: state.addressBook
})

export default connect(mapStateToProps)(AddressBookSelector);