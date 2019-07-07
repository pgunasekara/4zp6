import React from 'react';

import {Platform, StyleSheet, Text, View, FlatList, Button} from 'react-native';
import NavigatorMenu from './Navigator';

import ActionButton from 'react-native-action-button';
import Icon from 'react-native-vector-icons/Ionicons';
import PatientListItem from './PatientListItem';

import { GetPatients } from '../Lib/Api';

export default class PatientList extends React.Component {

    /*
        Set The Navbar title, and add a Navigation Menu with the options for Bands, 
        Options and to Sign Out. The Bands and Options are unimplemented and there
        for show, however the Sign out redirects to Home.
     */
    static navigationOptions = ({ navigation }) => {
        redirectOptions = [
            {
                title: "Bands",
                handler : () => alert("Not implemented yet")
            },
            {
                title: "Options",
                handler: () => alert("Not implemented yet")
            },
            {
                title:"Sign Out",
                handler: () => navigation.navigate('Home', {})
            }
        ]

        return {
            title: 'Patients',
            headerLeft: null,
            headerRight : <NavigatorMenu options={redirectOptions}/>
        }
    }

    constructor(props){
        super(props);
        this.state = {};
        this.reloadList = this.reloadList.bind(this);
    }

    /**
     * Reload the patient list and set the state to include the patients
     */
    reloadList(){
        GetPatients().then((patientsJson) => {
            this.setState({ patients: patientsJson });
        });
    }

    /*
        Default method that gets called when the component is first mounted to be rendered.
     */
    componentDidMount() {
        this.reloadList();
    }

    render() {
        const { navigate } = this.props.navigation;
        return (
            <View style={{ flex: 1 }}>
                <FlatList
                    data={this.state.patients}
                    renderItem={({ item, index }) => (
                        <PatientListItem
                            navigation={this.props.navigation}
                            firstName={item.firstName}
                            lastName={item.lastName}
                            firstRow={index == 0}
                            id={item.id}
                            deviceId={item.deviceId}
                        />
                    )}
                />

                {/* Rest of the app comes ABOVE the action button component !*/}
                <ActionButton buttonColor="rgba(231,76,60,1)"
                    onPress={() => {
                        navigate('PatientForm', {
                            update: false,
                            onGoBack : () => this.reloadList()
                        })
                    }}
                    degrees={0}>
                    <Icon name="md-create" style={styles.actionButtonIcon} />
                </ActionButton>

            </View>
        );
    }
}

const styles = StyleSheet.create({
    actionButtonIcon: {
        fontSize: 20,
        height: 22,
        color: 'white',
    },
});
