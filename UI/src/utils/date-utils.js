(function(dateutil, undefined) {
    var FINNISH_DATE_FORMAT = 'D.M.YYYY';
    var ISO_8601_DATE_FORMAT = 'YYYY-MM-DD';
    var FINNISH_PIKADAY_I18N = {
            previousMonth : 'edellinen kuukausi',
            nextMonth     : 'seuraava kuukausi',
            months: ['Tammikuu','Helmikuu','Maaliskuu','Huhtikuu','Toukokuu','Kesäkuu','Heinäkuu','Elokuu','Syyskuu','Lokakuu','Marraskuu','Joulukuu'],
            weekdays: ['sunnuntai','maanantai','tiistai','keskiviikko','torstai','perjantai','lauantai'],
            weekdaysShort : ['Su','Ma','Ti','Ke','To','Pe','La']
    };

    dateutil.iso8601toFinnish = function(iso8601DateString) {
        return _.isString(iso8601DateString) ? moment(iso8601DateString, ISO_8601_DATE_FORMAT).format(FINNISH_DATE_FORMAT) : "";
    };

    dateutil.finnishToIso8601 = function(finnishDateString) {
        return moment(finnishDateString, FINNISH_DATE_FORMAT).format(ISO_8601_DATE_FORMAT);
    };

    dateutil.todayInFinnishFormat = function() {
        return moment().format(FINNISH_DATE_FORMAT);
    };

    dateutil.addFinnishDatePicker = function(element, config) {
        return new Pikaday(jQuery.extend({}, {
            field: element,
            format: FINNISH_DATE_FORMAT,
            firstDay: 1,
            yearRange: [1950, 2050],
            i18n: FINNISH_PIKADAY_I18N
        }, config));
    };
}(window.dateutil = window.dateutil || {}));