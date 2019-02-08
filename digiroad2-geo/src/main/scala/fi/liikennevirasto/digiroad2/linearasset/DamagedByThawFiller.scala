package fi.liikennevirasto.digiroad2.linearasset

import fi.liikennevirasto.digiroad2.asset.Asset.DatePropertyFormat
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.linearasset.LinearAssetFiller.ChangeSet
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

class DamagedByThawFiller extends AssetFiller {
  val ActivePeriod = "spring_thaw_period"
  val Repetition = "annual_repetition"
  val todayString: String = DateTime.now().toString(DatePropertyFormat)
  val today = DateTime.now()
  val dateFormat = "MM-dd"

  override protected def updateValues(roadLink: RoadLink, assets: Seq[PersistedLinearAsset], changeSet: ChangeSet): (Seq[PersistedLinearAsset], ChangeSet) = {

    def getProperties(publicId: String, propertyData: Seq[DynamicProperty]): Seq[DynamicPropertyValue] = {
      propertyData.find(p => p.publicId == publicId) match {
        case Some(props) => props.values
        case _ => Seq()
      }
    }

    def getProperty(publicId: String, propertyData: Seq[DynamicProperty]): Option[DynamicPropertyValue] = {
      propertyData.find(p => p.publicId == publicId) match {
        case Some(props) => props.values.headOption
        case _ => None
      }
    }

    def toCurrentYear(period: DatePeriod): DatePeriod = {
      val endDate = period.endDate.get
      val thisYear = today.getYear
      val endDateYear = endDate.getYear
      val difference = thisYear - endDateYear
      if(difference > 0)
        DatePeriod(Some(period.startDate.get.plusYears(difference)), Some(period.endDate.get.plusYears(difference)))
      else
        period
    }

    def needsUpdate(value: DynamicPropertyValue): Boolean = {

      val period = value.value.asInstanceOf[DatePeriod]

      val endDate = period.endDate.get
      val thisYear = today.getYear
      val endDateYear = endDate.getYear

      val monthDay = DateTimeFormat.forPattern("MM-dd")

      val parsedCurrentDate = monthDay.parseDateTime(today.toString(dateFormat))
      val parsedEndDate = monthDay.parseDateTime(period.endDate.get.toString(dateFormat))

      thisYear - endDateYear > 0 || (thisYear - endDateYear >= 0 && parsedEndDate.isAfter(parsedCurrentDate))
    }
    //    val (toUpdate, unaffected) = segments.partition { asset =>
    //      asset.value.map(_.asInstanceOf[DynamicValue].value.properties).map { propertyData =>
    //        getProperty(Repetition, propertyData).map(x => x.value) match {
    //          case Some(checked) => if (checked.equals("1")) {
    //            val periods = getProperties(ActivePeriod, propertyData)
    //            periods.map { period =>
    //              if (inPeriod(period))
    //                period.copy(toCurrentYear(period.value.asInstanceOf[DatePeriod]))
    //              else
    //                period
    //            }
    //          }
    //          case _ => propertyData
    //        }
    //      }
    //
    //      true
    //    }

    //    val box2 = assetValues.find(x => x.find(_.publicId == Repetition))
    //    val box = assetValues.map(x => x.find(_.publicId == Repetition))
    //val periodi =

    def isRepeated(checkbox: Option[DynamicPropertyValue]): Boolean = {
      checkbox.exists(x => x.value.asInstanceOf[String].equals("1"))
    }
    def needUpdates(properties: Seq[DynamicProperty]): Boolean = {
      isRepeated(getProperty(Repetition, properties)) &&
        getProperties(ActivePeriod, properties).forall { period =>
          needsUpdate(period)
        }
    }

    val (noneNeeded, toUpdate) = assets.partition( asset =>
      asset.value.map(_.asInstanceOf[DynamicValue].value.properties).forall {
        propertyData => !needUpdates(propertyData)
      }
    )

//    val tester = assets.map { asset =>
//      val assetValues = asset.value.map(_.asInstanceOf[DynamicValue].value.properties)
//      val assetProperties = assetValues.map { propertyData =>
//        getProperty(Repetition, propertyData).map(x => x.value) match {
//          case Some(checked) => if(checked.equals("1")) {
//            val periods = getProperties(ActivePeriod, propertyData)
//            val periods1 = periods.map { period =>
//              if(inPeriod(period))
//                period.copy(toCurrentYear(period.value.asInstanceOf[DatePeriod]))
//              else
//                period
//            }
//            periods1
//          }
//          case _ => propertyData
//        }
//      }
//      1
//    }


    //    val tester = segments.map { asset =>
    //      val assetValues = asset.value.map(_.asInstanceOf[DynamicValue].value.properties)
    //      val assetProperties = assetValues.map { propertyData =>
    //        getProperty(Repetition, propertyData).map(x => x.value) match {
    //          case Some(checked) => if(checked.equals("1")) {
    //            val periods = getProperties(ActivePeriod, propertyData)
    //            val adjustedPeriods = periods.map { period =>
    //              if(inPeriod(period)){
    //                val newPeriod = period.copy(annualCorrection(period))
    //                val check = 1
    //              }
    //              else
    //                period
    //            }
    //          }
    //          case _ => assetValues
    //        }
    //      }
    //    }

    //    val tester = segments.map { asset =>
    //      val assetValues = asset.value.map(_.asInstanceOf[DynamicValue].value.properties)
    //      val assetProperties = assetValues.map { propertyData =>
    //        val periods = getProperties(ActivePeriod, propertyData)
    //        val checkBox = getProperty(Repetition, propertyData).map(x => x.value).get
    //        val adjustedPeriods = periods.map { period =>
    //          if(inPeriod(period) && checkBox.equals("1")){
    //            val newPeriod = period.copy(annualCorrection(period))
    //            val check = 1
    //          }
    //          else
    //            period
    //        }
    //      }
    //    }

    //    segments.map { asset =>
    //      asset.copy(value = asset.value.map(_.asInstanceOf[DynamicValue].value.properties).map { prop =>
    //        getProperties(DatePeriod, prop)} match {
    //        case Some(properties) => properties.map(x =>
    //          if(inPeriod(x))
    //            annualCorrection(x)
    //        case _ => asset.value
    //     )
    //    )
    //  }





    (assets, changeSet)
  }
}
