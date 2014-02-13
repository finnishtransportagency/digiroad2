Oskari.clazz.define('Oskari.digiroad2.bundle.actionpanel.event.ValidityPeriodCheckedEvent',
  function(validityPeriod) {
    this._validityPeriod = validityPeriod;
  }, {
    __name: 'actionpanel.ValidityPeriodCheckedEvent',
    
    getName: function() {
      return this.__name;
    },

    getValidityPeriod: function() {
      return this._validityPeriod;
    }
  },
  {'protocol' : ['Oskari.mapframework.event.Event']}
);