import React, { Component } from 'react';
import { Text, View, Button, StyleSheet, TouchableOpacity } from 'react-native';

import DateTimePicker from 'react-native-modal-datetime-picker';
import moment from 'moment';


// Getting the current date
export default class GetDate extends Component {

    _showDateTimePicker = () => this.setState({ isDateTimePickerVisible: true });
    _hideDateTimePicker = () => this.setState({ isDateTimePickerVisible: false });
    _handleDatePicked = (date) => {
        console.log('A date has been picked: ', date);
        this._hideDateTimePicker();
        this.props.dateCallback(date);
        this.setState({date: date});
        var fDate = moment(this.state.date).format('MMMM Do YYYY');
        this.setState({formattedDate: fDate});
    };

    constructor(props) {
        super(props);
        this.state = {
            date: props.date,
            formattedDate: moment(props.date).format('MMMM Do YYYY'),
        };
    }

    render() {
        return (
            <View style={{ flex: 1 }}>
                <Button 
                    onPress={this._showDateTimePicker} 
                    title={moment(this.state.date).format('MMMM Do YYYY')}
                    color="#5DACBD"    
                >
                </Button>
                <DateTimePicker
                    isVisible={this.state.isDateTimePickerVisible}
                    onConfirm={this._handleDatePicked.bind(this)}
                    onCancel={this._hideDateTimePicker}
                    date={this.state.date}
                    customStyles={{
                        dateIcon: {
                            position: 'absolute',
                            left: 0,
                            top: 4,
                            marginLeft: 0
                        },
                        dateInput: {
                            marginLeft: 36
                        }
                    }}
                />
            </View>
        );
    }
}