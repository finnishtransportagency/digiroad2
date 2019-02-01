package fi.liikennevirasto.digiroad2.dataimport

import java.io.InputStreamReader

import fi.liikennevirasto.digiroad2.Digiroad2Context.properties
import fi.liikennevirasto.digiroad2.{DigiroadEventBus, _}
import fi.liikennevirasto.digiroad2.asset._
import fi.liikennevirasto.digiroad2.authentication.RequestHeaderAuthentication
import fi.liikennevirasto.digiroad2.middleware.{AdministrativeValues, CsvDataImporterInfo}
import fi.liikennevirasto.digiroad2.user.UserProvider
import fi.liikennevirasto.digiroad2.util.MassTransitStopExcelDataImporter
import javax.servlet.ServletException
import org.joda.time.DateTime
import org.json4s.{CustomSerializer, DefaultFormats, Formats, JString}
import org.scalatra._
import org.scalatra.servlet.{FileItem, FileUploadSupport, MultipartConfig}
import org.scalatra.json.JacksonJsonSupport
import fi.liikennevirasto.digiroad2.asset.Asset._

class ImportDataApi extends ScalatraServlet with FileUploadSupport with JacksonJsonSupport with RequestHeaderAuthentication {

  case object DateTimeSerializer extends CustomSerializer[DateTime](format => ( {
    case _ => throw new NotImplementedError("DateTime deserialization")
  }, {
    case d: DateTime => JString(d.toString(DateTimePropertyFormat))
  }))

  protected implicit val jsonFormats: Formats = DefaultFormats + DateTimeSerializer
  private val CSV_LOG_PATH = "/tmp/csv_data_import_logs/"

  lazy val csvDataImporter = new CsvDataImporter
  private final val threeMegabytes: Long = 3*1024*1024
  lazy val user = userProvider.getCurrentUser()
  lazy val eventbus: DigiroadEventBus = {
    Class.forName(properties.getProperty("digiroad2.eventBus")).newInstance().asInstanceOf[DigiroadEventBus]
  }

  lazy val userProvider: UserProvider = {
    Class.forName(properties.getProperty("digiroad2.userProvider")).newInstance().asInstanceOf[UserProvider]
  }

  before() {
    contentType = formats("json")
    configureMultipartHandling(MultipartConfig(maxFileSize = Some(threeMegabytes)))
    try {
      authenticateForApi(request)(userProvider)
    } catch {
      case ise: IllegalStateException => halt(Unauthorized("Authentication error: " + ise.getMessage))
    }
    response.setHeader("Digiroad2-Server-Originated-Response", "true")
  }

  post("/maintenanceRoads") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
   importMaintenanceRoads(fileParams("csv-file"))
  }

  post("/trafficSigns") {
    val municipalitiesToExpire = request.getParameterValues("municipalityNumbers") match {
      case null => Set.empty[Int]
      case municipalities => municipalities.map(_.toInt).toSet
    }

    if (!(user.isOperator() || user.isMunicipalityMaintainer())) {
      halt(Forbidden("Vain operaattori tai kuntaylläpitäjä voi suorittaa Excel-ajon"))
    }

    if (user.isMunicipalityMaintainer() && municipalitiesToExpire.diff(user.configuration.authorizedMunicipalities).nonEmpty) {
      halt(Forbidden(s"Puuttuvat muokkausoikeukset jossain listalla olevassa kunnassa: ${municipalitiesToExpire.mkString(",")}"))
    }

   importTrafficSigns(fileParams("csv-file"), municipalitiesToExpire)
  }

  post("/roadlinks") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }

    importRoadLinks(fileParams("csv-file"))
  }

  post("/massTransitStop") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val administrativeClassLimitations: Set[AdministrativeClass] = Set(
      params.get("limit-import-to-roads").map(_ => State),
      params.get("limit-import-to-streets").map(_ => Municipality),
      params.get("limit-import-to-private-roads").map(_ => Private)
    ).flatten

    importMassTransitStop(fileParams("csv-file"), administrativeClassLimitations)
  }

  get("/log/:id") {
    ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("" +
      "" +
      "" +
      "" +
      "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Morbi sed neque sit amet felis facilisis euismod hendrerit eget quam. Praesent condimentum sed est pretium venenatis. Aenean suscipit, augue ut vulputate porttitor, ip" +
      "sum ex vestibulum ante, eget varius nisi neque ut felis. Integer ac sapien non erat dictum luctus. Integer vehicula ullamcorper diam, ac sodales turpis feugiat iaculis. Praesent convallis velit eget turpis pretium, ut tempor lorem vehic" +
      "ula. Aliquam eu viverra elit. Suspendisse imperdiet dapibus aliquam. Etiam non erat vel erat fermentum ornare.\n\nPellentesque vel ex eros. Vivamus consectetur augue et massa faucibus, eget rutrum elit feugiat. Ut porttitor ex ac magna rhoncus, vel lacinia ligula cursus. Etiam malesuada et massa sed dictum. Phasellus ut sodales sapien. Pellentesque sagittis velit eget sagittis gravida. Fusce pharetra, neque eu suscipit semper, nisi odio pulvinar enim, faucibus ullamcorper arcu mi at ligula. Nam ac diam et arcu consequat mollis. Nullam tempor sollicitudin egestas. Pellentesque vitae lectus purus. Morbi blandit ex enim, ac tincidunt enim euismod et.\n\nQuisque ante neque, convallis sed consequat sit amet, dictum vitae lorem. Curabitur blandit augue ac arcu venenatis varius. Nunc interdum urna auctor, faucibus ante non, congue lorem. Nunc convallis dignissim leo. Integer gravida risus dignissim, pulvinar tellus at, ultrices arcu. Vestibulum elementum orci a justo laoreet iaculis. Praesent dui arcu, ullamcorper euismod sagittis at, viverra in nisi. Pellentesque scelerisque mi dapibus, vulputate nulla eget, pharetra mi.\n\nNam venenatis non nibh eu rhoncus. Fusce risus dui, sodales eu massa a, vestibulum pellentesque lorem. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Nam venenatis consectetur tortor suscipit convallis. Nulla interdum erat massa, eget convallis lectus egestas a. Pellentesque sit amet ante et nisi finibus fringilla. Duis accumsan augue id rhoncus auctor. Maecenas id porta orci. Aenean vehicula finibus bibendum. Suspendisse potenti. Duis congue convallis cursus. Etiam eget nunc sed mauris suscipit laoreet. Praesent at auctor arcu.\n\nPellentesque ac iaculis enim, in consequat nulla. Praesent et metus feugiat, pharetra urna ac, elementum metus. Praesent sagittis venenatis magna, et luctus neque congue quis. Duis fermentum iaculis nibh nec suscipit. Quisque et elit a metus sollicitudin dapibus. Curabitur bibendum lectus ut felis ornare, at venenatis ligula imperdiet. Proin suscipit id eros fringilla dapibus. Pellentesque tempor leo nibh, in interdum ex viverra a. Nunc quis augue sit amet ipsum aliquet placerat et porttitor sem.\n\nNunc a tempus metus, a aliquet diam. Pellentesque nec tellus ac mauris ultricies mollis non ac enim. Pellentesque pretium purus dui, vitae auctor orci feugiat et. Duis bibendum porta nisl, in elementum arcu pharetra sed. Sed cursus nulla in dolor convallis, nec pulvinar arcu placerat. Cras enim sem, suscipit vitae tristique at, varius a risus. Sed et libero elit. Aliquam et lobortis sapien. Curabitur at augue eget arcu rutrum tincidunt at at ipsum. Proin tincidunt, augue at venenatis auctor, nisl augue interdum ipsum, vel viverra lectus dolor eu nunc. Donec ultricies tristique quam, et fermentum neque consectetur et.\n\nNam at lacinia arcu. In venenatis quam enim, ac tristique lacus iaculis at. Morbi porta sem et lectus viverra pellentesque. Vestibulum ut justo posuere, tempus elit nec, sollicitudin felis. Aliquam nec odio tellus. Phasellus in ante eget diam cursus aliquet eget ut diam. Etiam at velit ac metus viverra suscipit ut non tellus. Nulla eu aliquam felis, vitae dignissim ligula. Vestibulum sodales leo mi, vitae suscipit ex consectetur ac. Pellentesque lobortis mauris eget nisi porttitor, in sagittis elit rutrum. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Nullam lacinia massa eget dapibus fermentum. Proin convallis sodales nisi at efficitur.\n\nMauris quis congue nisi, posuere condimentum diam. Nulla dapibus dapibus tincidunt. Sed non ante diam. Vestibulum volutpat eget nunc quis egestas. Curabitur efficitur ex urna, vitae consectetur nunc euismod at. Donec aliquam turpis sapien, vel lobortis purus congue eu. Mauris eleifend consequat justo ac sodales. Sed metus velit, efficitur pulvinar blandit vitae, viverra fringilla libero. Nunc sit amet est bibendum massa aliquam luctus. In suscipit neque sed consectetur vulputate. Donec aliquet tincidunt lacinia. Quisque interdum ex sed turpis laoreet fermentum. Phasellus ante quam, dignissim ac tincidunt nec, ultricies non turpis. Integer dapibus tortor mi, sit amet efficitur mauris pellentesque quis. Aliquam vel egestas nunc. Donec non mollis ligula.\n\nUt ex diam, molestie nec lacus a, sodales imperdiet sem. Quisque tincidunt cursus arcu, in fringilla lacus accumsan eget. Phasellus pulvinar, eros et fringilla placerat, tortor risus hendrerit neque, venenatis vulputate dolor felis eget urna. Donec vel placerat nibh, et mattis dolor. Proin condimentum nulla ut turpis hendrerit pulvinar. Aenean varius nisl orci, at auctor massa vestibulum posuere. Nullam ut aliquet nibh. Curabitur ullamcorper diam quis nisi pharetra sagittis. Morbi semper pharetra ipsum, ac cursus elit laoreet nec. Donec nec gravida tortor. Sed venenatis ante sed rhoncus lacinia. Sed a felis justo. Mauris efficitur augue nisi, nec rutrum diam vehicula vitae.\n\nUt finibus sapien ac libero dictum ornare. Nullam placerat finibus diam tincidunt vehicula. Donec bibendum arcu id blandit tempus. Nulla consequat accumsan sapien, id dignissim dolor varius non. Nunc sed posuere enim. Aliquam dignissim interdum felis, ac sollicitudin sapien feugiat et. Nunc hendrerit maximus congue. Praesent a lorem nec sem pretium consectetur a a turpis.\n\nProin posuere nisi eget facilisis ullamcorper. Praesent ac pulvinar eros. Nam vehicula tortor sed felis consectetur imperdiet. Sed eleifend egestas commodo. Vivamus iaculis ligula dolor, eu maximus quam auctor nec. Etiam viverra rutrum ligula, quis dictum lorem volutpat nec. Donec sed enim vel risus faucibus vehicula. Ut a elit odio. Donec vitae enim eu sapien rhoncus lobortis eu sed neque. Donec dapibus ut ante eget molestie.\n\nMauris sollicitudin eleifend purus nec sollicitudin. Praesent sed ornare mauris. Nulla at tempus arcu. Nunc quis finibus urna, malesuada gravida eros. Sed malesuada lorem eu ante vulputate, quis euismod leo suscipit. Donec sem ex, consequat ut ligula vel, condimentum scelerisque purus. Cras sagittis arcu quis dignissim auctor. Ut ac lacus eget augue vehicula mollis a vitae urna. Suspendisse fermentum tempor quam ac elementum. Proin eget enim consectetur, aliquet eros vel, efficitur tortor. Pellentesque convallis luctus nunc ut ultrices. Nulla egestas, purus a placerat cursus, sapien lorem dignissim ante, id fermentum metus nibh vel felis. Fusce egestas accumsan bibendum.\n\nEtiam quis facilisis neque. Integer eget ipsum elit. In hac habitasse platea dictumst. Vestibulum eget turpis ac tellus posuere interdum et facilisis quam. Proin volutpat porttitor euismod. Nulla a ligula eget dui mollis hendrerit. Praesent commodo justo vel magna pellentesque, sit amet feugiat enim ultricies.\n\nDonec enim erat, fermentum quis lacus tristique, aliquet convallis sapien. Ut felis magna, egestas sed odio faucibus, commodo mattis magna. Nullam arcu justo, tristique dictum dapibus aliquam, eleifend a nisi. Curabitur vulputate enim turpis, eu ullamcorper nulla venenatis cursus. Proin quis ornare neque. Cras sagittis pellentesque consequat. Duis nunc odio, lobortis et posuere consectetur, placerat vel lacus. Duis mauris orci, auctor nec bibendum a, fringilla et augue. Donec vulputate pretium semper. Morbi et cursus nisi, sit amet cursus arcu.\n\nDonec eget dolor ut elit commodo laoreet. Nulla ultrices sapien a est ultricies laoreet. Nam in felis id ipsum malesuada ullamcorper sed vel mi. Aliquam at quam nisl. Nullam eros nibh, pharetra nec iaculis volutpat, dignissim at felis. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Fusce hendrerit in nibh sed tincidunt.\n\nVestibulum malesuada quis lectus in condimentum. Suspendisse a mauris vel odio eleifend hendrerit. Phasellus tempor lacus quis egestas porta. Phasellus aliquet nulla neque, eu volutpat sem volutpat at. Cras erat lacus, mollis a orci id, rhoncus fringilla urna. Duis sit amet accumsan orci. Morbi maximus ipsum varius aliquam pellentesque. Nunc sodales sapien in risus rhoncus, sit amet iaculis elit sollicitudin. Aenean varius, diam at luctus gravida, ante diam tristique ante, ac fermentum lacus turpis sed turpis.\n\nPraesent laoreet ligula sed augue bibendum ultrices. Donec porta auctor ipsum, sed tempus neque pulvinar eu. Praesent feugiat rhoncus ligula, eget eleifend lacus ultrices a. Vestibulum" +
      " quis semper erat. Morbi nibh ante, bibendum eu nisl et, imperdiet vulputate augue. Aliquam molestie sit amet mauris sed imperdiet. In accumsan egestas lorem, eu commodo dui dictum a. Nulla facilisi. Proin posuere ipsum non mollis accumsan. Aliquam convallis eget diam eget suscipit. Nunc neque magna, porttitor cursus neque ac, facilisis laoreet elit. Fusce laoreet ex et erat fermentum, in tempus urna elementum. Maecenas quis consequat nunc, vel bibendum ex. Sed nunc tellus, convallis at magna ac, eleifend dignissim metus.\n\nNullam ultricies consectetur leo sed pulvinar. Quisque metus sapien, ullamcorper eu luctus id, aliquet quis lectus. Suspendisse potenti. Integer id facilisis sapien, eget ultricies augue. Curabitur tempor est eget egestas ultrices. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Vivamus viverra non purus eu molestie. Donec sit amet sem non leo sagittis euismod sit amet nec leo. Nullam volutpat diam elit, sit amet fringilla urna pellentesque vitae. Praesent a dui faucibus, volutpat diam sed, porta dolor. Nunc et nibh mollis, dignissim enim a, mollis ex.\n\nMorbi orci orci, tincidunt sit amet blandit quis, interdum quis felis. Donec pulvinar magna ac venenatis convallis. Proin vitae ipsum risus. Fusce tempor vestibulum nunc at fringilla. Etiam est magna, faucibus ac magna quis, volutpat ullamcorper sapien. Phasellus a vehicula turpis. Nam lobortis lacus augue, eget pellentesque massa iaculis in.\n\nDonec semper risus eget tortor tempor, quis placerat orci vulputate. Donec id nulla elit. Donec fermentum nunc gravida erat molestie aliquam. Donec bibendum lobortis scelerisque. Cras pharetra semper magna sit amet auctor. Nam vulputate tincidunt nisi eu rutrum. Duis iaculis tortor massa, vitae lobortis ligula pulvinar quis. Mauris a ultricies lacus. In elit leo, posuere gravida tristique vitae, vestibulum ut velit.\n\nNullam et dignissim sem. Praesent leo ex, varius at iaculis congue, ultrices vel mauris. Curabitur at imperdiet tortor. Donec rutrum lacus a tortor egestas luctus. Suspendisse faucibus felis a accumsan pharetra. Fusce congue mollis nisi, eu maximus felis eleifend in. Curabitur pharetra condimentum felis gravida commodo. Donec pellentesque imperdiet sollicitudin. Duis ut aliquam massa. Curabitur quis convallis tortor. Quisque metus eros, vehicula gravida euismod id, tristique non purus. Praesent mauris est, varius malesuada ligula at, lobortis ultricies nunc. Sed at lacinia lorem.\n\nDonec vel tellus non justo hendrerit dignissim a sit amet orci. Aenean a urna non est venenatis ultrices a sit amet odio. Cras lorem est, lobortis quis tellus quis, lobortis luctus lectus. Quisque aliquam, dui faucibus dictum fermentum, metus felis cursus arcu, aliquet lacinia diam eros commodo ligula. Cras placerat, lorem eu commodo molestie, urna elit ornare dolor, in ullamcorper risus elit sollicitudin dui. Proin in fermentum sem. Ut sem velit, tempor sit amet sagittis eu, eleifend sit amet libero. Maecenas nec augue massa. Quisque pharetra justo eu tellus ultricies, fermentum feugiat urna pretium. Nam non ex ac libero eleifend blandit. Morbi id auctor nulla, in maximus augue. Mauris maximus turpis sed augue congue, id volutpat neque fringilla. Integer velit nulla, sagittis eu neque vitae, porttitor ultricies eros.\n\nPraesent vel dolor purus. Suspendisse potenti. Ut tempor tristique ultrices. Integer bibendum finibus venenatis. Curabitur pharetra rhoncus risus at malesuada. Integer condimentum, augue id sollicitudin tincidunt, libero tellus ullamcorper felis, id tincidunt lacus mi nec nibh. Fusce nec lorem maximus, sagittis sapien vel, pharetra lorem. Duis aliquam posuere magna vitae varius. Nullam posuere malesuada erat ut interdum. Sed mattis quam commodo est faucibus, in fermentum odio dignissim. Vivamus massa augue, vehicula eu odio in, viverra lacinia felis. Vestibulum lobortis enim id laoreet aliquam. Nulla venenatis augue dolor, ac aliquam quam rutrum a. Nulla a maximus massa, vel imperdiet elit.\n\nVestibulum turpis justo, hendrerit sit amet leo sit amet, ullamcorper sodales odio. Curabitur pellentesque ac lorem ac pulvinar. Sed nisi sem, pellentesque non fermentum a, lacinia nec libero. Orci varius natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Duis mollis condimentum mattis. Proin elementum lorem eget sem maximus egestas. Donec luctus venenatis arcu, quis suscipit lectus rutrum non. Proin ut urna eget ex viverra vestibulum sit amet vel lectus. Vivamus sollicitudin varius augue, non pharetra metus varius at. Vestibulum vel ultrices elit. Phasellus gravida enim in fringilla iaculis. Vestibulum sed pulvinar justo. Phasellus eu tincidunt ipsum, ac tincidunt risus. In et congue nibh, quis tincidunt turpis. Mauris eu gravida erat, vitae molestie nisi. Integer eu enim at mi blandit suscipit in non sem.\n\nVivamus quis aliquam urna. Aliquam at congue elit. Integer urna est, consequat id tempor vitae, porttitor at odio. In id sem nisl. Curabitur consequat sapien in dui varius, a pulvinar nibh molestie. Pellentesque metus eros, viverra id sollicitudin eu, pulvinar vitae massa. Vestibulum ipsum mi, aliquam auctor interdum quis, tincidunt sed elit. Ut ac quam sit amet felis hendrerit maximus sit amet tincidunt lorem. Aliquam erat volutpat. Fusce scelerisque ex et cursus dignissim. Pellentesque fringilla erat in nisl ullamcorper, sit amet dictum purus sagittis. Aliquam erat volutpat. Cras enim est, iaculis tincidunt risus sed, efficitur malesuada metus. Donec sed tincidunt tellus.\n\nPellentesque sodales nulla neque. Integer tincidunt ultrices consequat. Vestibulum ac mauris orci. Vivamus vitae luctus ligula. Sed non risus vitae massa dapibus condimentum. Mauris pretium sit amet tellus blandit lobortis. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Suspendisse sed sem diam. Proin varius semper lorem ac venenatis. Pellentesque auctor turpis nec nibh aliquam, at vehicula nulla hendrerit. Sed convallis elit gravida mauris ullamcorper, nec suscipit elit imperdiet.\n\nProin at arcu pellentesque, lobortis nibh ut, viverra ante. Quisque sed neque pellentesque, hendrerit eros id, condimentum ex. Etiam rhoncus tortor nec est commodo malesuada. Nunc sem libero, euismod sit amet volutpat at, sagittis eget tortor. Praesent molestie magna id porttitor ornare. Aliquam tempor scelerisque vestibulum. Curabitur porta purus ipsum, a imperdiet mi scelerisque et. Curabitur viverra dapibus urna, id malesuada sem. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Vestibulum iaculis magna a lorem vestibulum, vitae tempor velit gravida. Morbi ullamcorper nec ipsum nec accumsan. Proin a pellentesque sapien. Cras aliquet nulla vel sem vehicula maximus. Aliquam euismod lectus dolor, sit amet luctus nisi tempor quis. Phasellus mollis orci aliquet, ultricies massa at, vehicula dui.\n\nNunc volutpat enim non lacus congue, quis tempus nisi rutrum. Nullam rhoncus cursus tortor. Cras eu sagittis sapien. Fusce nibh purus, fermentum nec quam in, suscipit congue urna. Praesent semper nisi suscipit, sollicitudin lectus vel, congue lectus. Fusce at magna id leo porttitor rutrum quis et sem. Vivamus laoreet feugiat scelerisque. Nam blandit lacus sit amet mauris sagittis congue ut ac lorem. Ut augue magna, efficitur non dui et, tincidunt posuere enim. Praesent vel luctus purus. Aliquam mauris sapien, porttitor commodo massa vitae, pretium porta arcu. In a lacus nisi. Pellentesque vitae arcu a orci mollis ultricies in id erat. Curabitur at aliquam sem. Nam eros sem, venenatis ac feugiat vel, posuere at orci. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.\n\nVivamus ullamcorper est vitae odio pulvinar efficitur. Vestibulum vehicula mauris vitae mauris tincidunt porttitor. Etiam viverra, ligula et tempus feugiat, mauris tortor porta ipsum, eu accumsan ex felis ac est. Etiam fermentum pellentesque bibendum. Nunc nec erat quis ligula condimentum aliquet eget a massa. Praesent elementum urna quis libero rhoncus mattis. Aliquam posuere ornare urna at ultricies. Etiam tempus nec magna ac dapibus. Duis dignissim sapien magna, ac dapibus massa venenatis eget.\n\nVivamus at ornare elit, et scelerisque ex. Etiam faucibus nisl et blandit pharetra. Donec eleifend lectus quis magna venenatis placerat. Etiam porta ipsum purus, sit amet tempus justo posuere quis. Vivamus sed ex libero. Nam vestibulum ex quis velit ultricies laoreet. Pellentesque dignissim vestibulum ante. Sed blandit at augue eu eleifend. Nunc tempus nulla placerat commodo ultrices. Integer eget tincidunt velit, at blandit elit. Mauris rhoncus felis felis, a luctus nunc pellentesque a.\n\nVestibulum congue hendrerit orci, sed fringilla nisl mollis ac. Phasellus rutrum urna eu nisi dictum dignissim. Aliquam quis rutrum orci, et congue magna. Pellentesque eget metus sit amet neque malesuada ullamcorper. Morbi ac aliquet mi, ut gravida orci. Praesent eu mattis velit, et imperdiet neque. In in condimentum libero. Aliquam quis vehicula magna, finibus ultricies arcu. Aliquam at odio a lorem maximus aliquet eget in enim.\n\nFusce blandit eros sit amet laoreet gravida. Sed a arcu sed enim vehicula aliquet ut ut mauris. Proin luctus leo metus, ac hendrerit orci ultrices in. Nulla non nisi auctor, dapibus augue eget, tincidunt libero. Nunc dictum mattis varius. Pellentesque sed eros at urna imperdiet tincidunt. Vivamus eget lectus id lectus lacinia congue nec non neque. Morbi eleifend, orci in dictum hendrerit, arcu neque dictum enim, nec suscipit metus diam sed ex. Duis eget iaculis lectus. Pellentesque efficitur diam nec lacus venenatis, a tristique nulla finibus. Donec et est ac purus ultricies accumsan. Sed sagittis mauris quis imperdiet convallis.\n\nPraesent id neque lacinia, rutrum dui sit amet, pellentesque purus. Nullam dapibus, lacus sed ullamcorper rhoncus, massa est interdum nulla, quis dapibus metus leo eu ipsum. Duis ultricies fermentum metus, nec porttitor tellus viverra id. Cras et eros diam. Suspendisse neque lacus, rhoncus sed neque vitae, interdum cursus leo. Ut cursus id nulla sit amet ornare. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae;\n\nMaecenas volutpat quam massa, in imperdiet dolor imperdiet eget. Etiam eget urna ut sem consequat convallis. Ut dapibus massa vitae nisl lacinia dapibus. Duis gravida sollicitudin interdum. Vivamus nisi risus, sollicitudin interdum laoreet at, ullamcorper nec leo. Nam imperdiet urna a sodales pretium. Nulla facilisi. Maecenas auctor suscipit elit vitae porta.\n\nDuis suscipit tempus nunc at ornare. Vestibulum elementum dictum purus. Donec et blandit augue. Nullam vulputate ipsum ac libero interdum vestibulum. Proin egestas viverra ligula, in molestie sem egestas eu. In volutpat felis a pellentesque dapibus. Proin eget pharetra risus. Cras semper aliquam est, vel pharetra tortor. Maecenas aliquet, lectus ac egestas varius, libero diam sagittis odio, sit amet ornare diam orci sed eros. Suspendisse auctor feugiat justo.\n\nSed pulvinar suscipit aliquet. Nulla elementum mi sagittis, elementum lacus quis, ultricies arcu. Nulla turpis mi, euismod sit amet hendrerit sit amet, posuere sit amet nulla. Maecenas fringilla ultrices bibendum. Sed tortor nibh, convallis in vehicula sit amet, vehicula dictum urna. Pellentesque id sem mauris. Praesent tempus ultrices diam quis egestas. Nullam quis lobortis neque, in rhoncus orci. Nam placerat lacus sed purus pretium fermentum. Etiam sagittis risus eu vehicula laoreet. Nulla gravida efficitur orci, non placerat ex aliquet non. Curabitur congue odio ut erat varius tempus. Duis eu dolor nunc. In ullamcorper mollis ligula, ac convallis nunc auctor vitae.\n\nMaecenas convallis orci id mauris fermentum, eget laoreet magna feugiat. Phasellus eget urna nec ante tristique molestie ut eget mauris. Nullam scelerisque nisi vel nibh consectetur condimentum. In id purus volutpat, semper leo eget, gravida nunc. Aenean vel hendrerit elit, a egestas arcu. Pellentesque egestas eu orci eget porta. Nam ut accumsan tellus. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Curabitur accumsan justo leo, vel dictum est accumsan id. Integer convallis, diam eu tempor sodales, lacus erat blandit nibh, nec efficitur ex mi in libero. Fusce interdum metus justo, eget cursus magna dapibus id.\n\nNam in vehicula tellus. Fusce mauris ipsum, finibus a laoreet ac, placerat quis turpis. Duis dictum neque eu lorem tempus, vulputate mattis sem pellentesque. Etiam semper, ipsum nec porta aliquam, velit est mattis massa, non auctor massa felis luctus velit. Suspendisse potenti. Curabitur mollis nisl id nibh ultrices, dignissim ornare lacus sodales. Quisque mollis turpis tincidunt, vestibulum purus at, cursus urna. Duis pulvinar ac lacus id tristique. Vestibulum maximus dui finibus felis iaculis rutrum. Curabitur fringilla orci in sem sollicitudin lobortis. Aenean semper, neque a finibus aliquam, nisi justo ornare nisi, at dapibus metus mi ac lorem. Etiam consequat viverra odio, quis vehicula massa tempor a. Proin condimentum eget nisl nec malesuada. In ultrices, lorem ac congue blandit, enim erat laoreet elit, vel vulputate ipsum lacus in risus.\n\nVivamus vehicula gravida bibendum. Donec gravida commodo quam sit amet pretium. Phasellus vulputate lectus nec odio suscipit pellentesque. Pellentesque vel fermentum nibh. Proin in ante tortor. Nam id diam vel nisl sagittis bibendum ut non ante. Mauris fringilla leo et ipsum ultricies, vel rhoncus nibh ultricies.\n\nSed rhoncus euismod metus, a euismod enim faucibus et. Proin eu dapibus lectus. Nam pretium placerat risus, eu faucibus eros semper sit amet. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras vel quam non arcu bibendum dignissim. Vivamus at nunc est. Sed eleifend, elit ac posuere luctus, eros nulla laoreet nisl, in scelerisque augue nibh ac massa. Quisque eleifend, risus eget dictum sodales, neque nulla molestie urna, in venenatis sapien ligula elementum nisi. Etiam sed risus nec justo mattis tincidunt. Sed tincidunt lobortis pretium. Sed vel tellus convallis, tincidunt metus lacinia, pharetra orci. Phasellus et dignissim dui, vel fermentum lacus. Sed elementum tortor neque, et tempus risus posuere nec. Aenean porttitor ut diam eu ultrices. Suspendisse a consequat diam, in fermentum velit. Fusce auctor felis arcu.\n\nNulla consectetur, mauris sed congue elementum, felis neque interdum justo, eget mollis leo dolor ut augue. Maecenas vulputate massa ante, id varius quam ultrices et. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Maecenas justo lorem, sagittis quis tortor non, ultrices lacinia ipsum. Aliquam erat volutpat. Maecenas eu tortor a massa malesuada pretium ac consectetur sapien. Curabitur porttitor vehicula lacus quis ultrices. Nam laoreet finibus lorem a pulvinar.\n\nMaecenas arcu magna, sodales id tincidunt in, interdum a nulla. Aenean vulputate nisl non tempus finibus. Aliquam volutpat elementum est dictum congue. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Nullam tincidunt ut massa quis aliquam. Donec lectus turpis, aliquam vitae nisl at, posuere ultrices tortor. Donec ultricies pretium aliquam. Ut ullamcorper ac nulla varius molestie. Fusce ullamcorper, velit eu sollicitudin vestibulum, nisl eros pharetra elit, pellentesque fringilla urna arcu in ipsum. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Cras ac volutpat sapien. Fusce aliquet, ex id gravida convallis, neque ipsum pulvinar ex, id accumsan urna metus eu urna. In hac habitasse platea dictumst. Etiam facilisis finibus felis, sit amet aliquet ipsum suscipit id. Maecenas id sollicitudin enim. Nullam dictum tristique ligula, vitae feugiat urna tempor eu.\n\nCras in mollis enim. Donec gravida nibh eget erat egestas posuere. Donec lobortis vitae ante id tincidunt. Nunc aliquet felis nec finibus imperdiet. Maecenas mi orci, imperdiet ut mollis et, feugiat vitae quam. Nullam sed purus at leo sollicitudin rhoncus. Etiam dignissim dictum metus. Nulla laoreet justo in ex sollicitudin, at iaculis dui cursus. Quisque pulvinar scelerisque enim. Nam ex erat, consectetur in consequat in, consectetur a sem. Sed in aliquam purus, eget iaculis augue. Donec eu justo arcu. Vestibulum egestas interdum sapien at convallis. Nam id finibus erat, nec viverra eros. Cras consequat, lorem a finibus sagittis, lacus lacus consectetur purus, eget mollis augue elit ut leo. Aliquam risus tellus, vehicula in aliquet et, convallis et nisl.\n\nIn placerat ipsum nisi, condimentum lacinia enim euismod ultricies. Vestibulum sit amet quam vitae dolor suscipit viverra. Nulla vel augue vel sem laoreet laoreet in eget quam. Ut nunc tellus, tincidunt non dui bibendum, sagittis porta dui. Nam sed dui quis enim vestibulum pellentesque. Etiam eget imperdiet massa, id varius nunc. Pellentesque auctor ac dui quis sollicitudin. Nullam convallis ligula quis nulla sollicitudin, sed pharetra massa condimentum. Suspendisse congue, enim eu interdum consequat, lorem nunc gravida diam, sed dignissim lectus purus interdum diam. Vivamus eget felis id velit molestie euismod. Vivamus rutrum dictum sem, vitae maximus diam vehicula vel. Fusce tincidunt, nulla quis suscipit fermentum, dolor lorem pellentesque odio, id semper leo nisi quis ipsum. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos.\n\nUt mollis, felis eget tincidunt volutpat, metus enim rutrum tortor, eget fringilla mi dui in magna. Phasellus fermentum quam sit amet eleifend laoreet. Nam tristique elementum lorem, eget consectetur libero condimentum quis. Nulla urna urna, convallis nec tincidunt in, accumsan in nisl. Aenean ultrices varius turpis, id placerat lacus. Vivamus nisl erat, consectetur vel lobortis a, molestie quis mi. Aenean non nulla ac velit imperdiet semper quis nec arcu. Suspendisse at mattis elit. Vestibulum pretium porta lectus, eget ullamcorper leo semper vel. Donec vulputate efficitur tellus, at scelerisque lectus dapibus id. Quisque blandit aliquam felis eget semper. Vestibulum finibus et nulla nec congue. Vivamus nec odio ante. Integer et porta orci.\n\nSuspendisse at tincidunt sapien. Maecenas aliquam ultricies tortor nec dapibus. Sed accumsan lacus vitae dictum auctor. Donec accumsan augue tellus, in maximus tortor lacinia quis. Vivamus nec cursus metus, ac iaculis dui. Donec eleifend tincidunt congue. Nulla facilisi. Sed sagittis, ex a pellentesque vehicula, nulla tortor ultrices diam, ac blandit elit eros ut dol" +
      "r. Nullam lacinia quam non eros volutpat ornare. Quisque tempor efficitur scelerisque. In non sagittis lacus. In tincidunt sollicitudin urna id dictum. Aenean erat lacus, tempor eget mollis eget, accumsan vitae justo."))

  }

    get("/log/:id"){
//      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("just some small text"))
    }

//  get("/log/:id"){
//    ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("just some small text"))
//  }

//  get("/log"){
//    Seq()
//  }
  get("/log") {
    Seq(ImportStatusInfo(1, Status.NotOK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", None),
      ImportStatusInfo(1, Status.NotOK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", None),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", None),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.OK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.NotOK, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.InProgress, "filename1", Some("oskar"), Some(DateTime.now()), "mass transit stop", Some("this is the text content")),
      ImportStatusInfo(1, Status.Abend, "filename2", Some("kari"), Some(DateTime.now()), "traffic signs", Some("this is the text content")),
      ImportStatusInfo(1, Status.Unknown, "filename3", Some("erik"), Some(DateTime.now()), "road links", Some("this12312312312132312312tent"))).map(job => Map(
      "id" -> job.id,
      "status" -> job.status.value,
      "fileName" -> job.fileName,
      "createdBy" -> job.createdBy,
      "createdDate" -> job.createdDate,
      "assetType" -> job.logType,
      "content" -> job.content,
      "description" -> job.status.descriptionFi

    )
    )
  }

  //TODO check if this is necessary
  override def isSizeConstraintException(e: Exception) = e match {
    case se: ServletException if se.getMessage.contains("exceeds max filesize") ||
      se.getMessage.startsWith("Request exceeds maxRequestSize") => true
    case _ => false
  }

  //TODO check if this exist
  post("/csv") {
    if (!user.isOperator()) {
      halt(Forbidden("Vain operaattori voi suorittaa Excel-ajon"))
    }
    val csvStream = new InputStreamReader(fileParams("csv-file").getInputStream)
    new MassTransitStopExcelDataImporter().updateAssetDataFromCsvFile(csvStream)
  }

  def importTrafficSigns(csvFileItem: FileItem, municipalitiesToExpire: Set[Int]): Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    eventbus.publish("importCSVData", CsvDataImporterInfo(TrafficSigns.layerName, fileName, user.username, csvFileInputStream))

  }

  def importRoadLinks(csvFileItem: FileItem ): Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    eventbus.publish("importCSVData", CsvDataImporterInfo("roadLinks", fileName, user.username, csvFileInputStream))
  }

  def importMaintenanceRoads(csvFileItem: FileItem): Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName
    if (csvFileInputStream.available() == 0) halt(BadRequest("Ei valittua CSV-tiedostoa. Valitse tiedosto ja yritä uudestaan.")) else None

    eventbus.publish("importCSVData", CsvDataImporterInfo(MassTransitStopAsset.layerName, fileName, user.username, csvFileInputStream))
  }

  def importMassTransitStop(csvFileItem: FileItem, administrativeClassLimitations: Set[AdministrativeClass]) : Unit = {
    val csvFileInputStream = csvFileItem.getInputStream
    val fileName = csvFileItem.getName

    eventbus.publish("importCSVData", CsvDataImporterInfo(MaintenanceRoadAsset.layerName, fileName, user.username, csvFileInputStream, Some(administrativeClassLimitations.asInstanceOf[AdministrativeValues])))
  }
}